package it.gov.pagopa.common.util

import akka.actor.Actor
import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import scalaxbmodel.paginf.{AD, CtDatiVersamentoRPT, CtRichiestaPagamentoTelematico, OBEP, POValue}

import javax.xml.datatype.XMLGregorianCalendar
import scala.util.{Failure, Success, Try}

trait ValidateRpt extends NodoLogging {
  this: Actor =>

  val GIORNI_SCADENZA: Long = 30

  def checkDatiElementoListaRpt(iuv: String, ccp: String, idDominio: String, rpt: CtRichiestaPagamentoTelematico): Try[Boolean] = {
    if (
      iuv != rpt.datiVersamento.identificativoUnivocoVersamento ||
      ccp != rpt.datiVersamento.codiceContestoPagamento ||
      idDominio != rpt.dominio.identificativoDominio
    ) {
      Failure(DigitPaException("Dati di intestazione non coerenti con RPT", DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  def validate(ddataMap: ConfigData, idDominio: String, idPSP: String, rpt: CtRichiestaPagamentoTelematico, idCanale: String, isBolloEnabled: Boolean): Try[CtRichiestaPagamentoTelematico] = {
    val check: Try[CtRichiestaPagamentoTelematico] = for {
      _ <- DDataChecks.checkTipoVersamento(log, ddataMap, rpt.datiVersamento.tipoVersamento.toString)
      _ <- checkTipoVersamentoDatiVersamento(rpt)
      hasBolloDigitale = rpt.datiVersamento.datiSingoloVersamento.exists(_.datiMarcaBolloDigitale.isDefined)
      _ <- checkPspAbilitatoAlBollo(hasBolloDigitale, idPSP, isBolloEnabled)
      _ <- ccpNonNACheck(rpt.datiVersamento)
      _ <- ibanAddebitoCheck(rpt.datiVersamento)
      _ <- singleCheck(rpt.datiVersamento)
      _ <- importoCheck(rpt.datiVersamento)
      _ <- ibanChecks(ddataMap, rpt.datiVersamento, idDominio)
      _ <- dataCheck(rpt.dataOraMessaggioRichiesta, rpt.datiVersamento.dataEsecuzionePagamento)
      _ <- DDataChecks.checkCanale(log, ddataMap, idCanale, None)
    } yield rpt

    check recoverWith {
      case digitPaException: DigitPaException =>
        Failure(digitPaException)

      case ex: Throwable =>
        log.warn(ex, s"Errore durante la validazione dell'RPT [${ex.getMessage}]")
        Failure(exception.DigitPaException(ex.getMessage, DigitPaErrorCodes.PPT_SEMANTICA, ex))
    }
  }

  //https://corporate.sia.eu/jira/browse/NODO4-187
  protected def checkTipoVersamentoDatiVersamento(rpt: CtRichiestaPagamentoTelematico) = {
    rpt.datiVersamento.tipoVersamento.toString match {
      case Constant.TIPOVERSAMENTO_PO =>
        if (rpt.datiVersamento.datiSingoloVersamento.size > 1) {
          Failure(
            exception.DigitPaException(
              s"Il tipo di versamento indicato comporta la valorizzazione di un unico elemento datiSingoloVersamento (numero attuale elementi: ${rpt.datiVersamento.datiSingoloVersamento.size} )",
              DigitPaErrorCodes.PPT_SEMANTICA
            )
          )
        } else {
          Success(())
        }
      case _ => Success(())
    }
  }

  def checkPluginRedirectCanale(ddataMap: ConfigData, idCanale: String): Try[Boolean] = {
    DDataChecks
      .checkCanale(log, ddataMap, idCanale, None)
      .flatMap(canale => {
        val idPluginMyBank = DDataChecks.getConfigurationKeys(ddataMap, "idPlugin.mybank")
        canale.servPlugin.flatMap(idPlugin => ddataMap.plugins.get(idPlugin)) match {
          case Some(plugin) if plugin.idServPlugin == idPluginMyBank => Success(true)
          case None if canale.paymentModel == ModelloPagamento.IMMEDIATO.toString || canale.paymentModel == ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO.toString =>
            log.warn(s"Plugin mancante ma modello pagamento 'IMMEDIATO' o 'IMMEDIATO_MULTIBENEFICIARIO', idCanale: [$idCanale], canale.modelloPagamento: [${canale.paymentModel}]")
            Failure(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
          case _ if canale.paymentModel == ModelloPagamento.IMMEDIATO.toString || canale.paymentModel == ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO.toString =>
            checkUrlCanale(canale.redirect.protocol, canale.redirect.ip, canale.redirect.port, canale.redirect.path)
          case _ => Success(true)
        }
      })
  }

  private def checkPspAbilitatoAlBollo(hasBolloDigitale: Boolean, idPSP: String, isBolloEnabled: Boolean): Try[Boolean] = {
    if (!hasBolloDigitale || isBolloEnabled) {
      Success(true)
    } else {
      log.warn(s"PSP [$idPSP] non abilitato alla ricezione della marca da bollo digitale")
      Failure(exception.DigitPaException("PSP non abilitato alla ricezione della marca da bollo digitale", DigitPaErrorCodes.PPT_CANALE_SERVIZIO_NONATTIVO))
    }
  }

  private def ccpNonNACheck(ctDatiVersamentoRPT: CtDatiVersamentoRPT): Try[Boolean] = {
    if (ctDatiVersamentoRPT.tipoVersamento == POValue && ctDatiVersamentoRPT.codiceContestoPagamento.equalsIgnoreCase("n/a")) {
      val msg =
        "Il tipo di versamento indicato non ammette la valorizzazione del codiceContestoPagamento con valore n/a"
      log.warn(msg)
      Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  private def ibanAddebitoCheck(ctDatiVersamentoRPT: CtDatiVersamentoRPT): Try[Boolean] = {
    if (ctDatiVersamentoRPT.tipoVersamento == AD && (ctDatiVersamentoRPT.ibanAddebito.isEmpty)) {
      Failure(exception.DigitPaException("Il tipo di versamento indicato comporta la valorizzazione dell'iban di addebito", DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  private def singleCheck(ctDatiVersamentoRPT: CtDatiVersamentoRPT): Try[Boolean] = {
    if ((ctDatiVersamentoRPT.tipoVersamento == POValue || ctDatiVersamentoRPT.tipoVersamento == OBEP) && ctDatiVersamentoRPT.datiSingoloVersamento.length != 1) {
      val msg =
        s"Il tipo di versamento indicato comporta la valorizzazione di un unico elemento datiSingoloVersamento, ctDatiVersamentoRPT.tipoVersamento: [${ctDatiVersamentoRPT.tipoVersamento}], ctDatiVersamentoRPT.datiSingoloVersamento.length: [${ctDatiVersamentoRPT.datiSingoloVersamento.length}]"
      log.warn(msg)
      Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  private def importoCheck(ctDatiVersamentoRPT: CtDatiVersamentoRPT): Try[Boolean] = {
    val listaSingoliImporti: Seq[BigDecimal] =
      ctDatiVersamentoRPT.datiSingoloVersamento.map(x => x.importoSingoloVersamento)
    val sommaImporti: BigDecimal = listaSingoliImporti.sum
    if (ctDatiVersamentoRPT.importoTotaleDaVersare != sommaImporti) {
      val msg = "La somma degli importoSingoloVersamento deve coincidere con l'importoTotaleDaVersare"
      log.warn(msg)
      Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  private def dataCheck(dataOraMessaggioRichiesta: XMLGregorianCalendar, dataEsecuzionePagamento: XMLGregorianCalendar): Try[Boolean] = {
    val dataOraMessaggioRichiestaDate =
      dataOraMessaggioRichiesta.toGregorianCalendar.toZonedDateTime.toLocalDateTime.toLocalDate
    val dataEsecuzionePagamentoDate =
      dataEsecuzionePagamento.toGregorianCalendar.toZonedDateTime.toLocalDateTime.toLocalDate

    val dataScadenzaEsecuzionePagamento = dataOraMessaggioRichiestaDate.plusDays(GIORNI_SCADENZA)

    if (dataEsecuzionePagamentoDate.isBefore(dataOraMessaggioRichiestaDate)) {
      val msg =
        "Il campo dataEsecuzionePagamento deve avere un valore uguale o maggiore del campo dataOraMessaggioRichiesta"
      log.warn(msg)
      Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
    } else if (dataEsecuzionePagamentoDate.isAfter(dataScadenzaEsecuzionePagamento)) {
      val msg =
        "Il campo dataEsecuzionePagamento deve avere un valore non eccedente i 30 giorni dal valore del campo dataOraMessaggioRichiesta"
      log.warn(msg)
      Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
    } else {
      Success(true)
    }
  }

  def ibanChecks(ddataMap: ConfigData, ctDatiVersamentoRPT: CtDatiVersamentoRPT, idDominio: String): Try[Boolean] = {
    var ibanFailed: Seq[String] = Nil
    val list = ctDatiVersamentoRPT.datiSingoloVersamento.map(versamento => {
      val ibanAccreditoOpt = versamento.ibanAccredito
      val marcaDaBollo = versamento.datiMarcaBolloDigitale

      if (ibanAccreditoOpt.isDefined) {
        if (marcaDaBollo.isDefined) {
          val msg =
            s"La presenza di una marca da bollo digitale comporta la non valorizzazione dell'elemento ibanAccredito, iban: [${ibanAccreditoOpt.get}]"
          log.warn(msg)
          Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_SEMANTICA))
        } else {
          DDataChecks.checkIban(log, ddataMap, idDominio, ibanAccreditoOpt.get) map (_ => true) recoverWith {
            case ex @ DigitPaException(_, DigitPaErrorCodes.PPT_IBAN_NON_CENSITO, _, FaultId.NODO_DEI_PAGAMENTI_SPC, _, _, _, _) =>
              ibanFailed = ibanFailed.+:(ibanAccreditoOpt.get)
              throw ex
          }
        }
      } else {
        if (marcaDaBollo.isDefined) {
          Success(true)
        } else {
          val errorMessage = s"IBAN accredito o marca da bollo non presenti"
          log.warn(errorMessage)
          Failure(exception.DigitPaException(errorMessage, DigitPaErrorCodes.PPT_SEMANTICA))
        }
      }
    })

    val listAppoggio = ctDatiVersamentoRPT.datiSingoloVersamento.map(versamento => {
      val ibanAppoggioOpt = versamento.ibanAppoggio

      if (ibanAppoggioOpt.isDefined) {
        DDataChecks.checkIban(log, ddataMap, idDominio, ibanAppoggioOpt.get) map (_ => true) recoverWith {
          case ex @ DigitPaException(_, DigitPaErrorCodes.PPT_IBAN_NON_CENSITO, _, FaultId.NODO_DEI_PAGAMENTI_SPC, _, _, _, _) =>
            ibanFailed = ibanFailed.+:(ibanAppoggioOpt.get)
            throw ex
        }
      } else {
        Success(true)
      }

    })

    if ((list ++ listAppoggio).exists(_.isFailure)) {
      val throwable = if (list.exists(_.isFailure)) {
        list.find(_.isFailure).get.failed.get.asInstanceOf[DigitPaException]
      } else {
        listAppoggio.find(_.isFailure).get.failed.get.asInstanceOf[DigitPaException]
      }
      if (ibanFailed.isEmpty) {
        Failure(throwable)
      } else {
        val msg =
          s"I valori di IBAN indicati nei versamenti [${ibanFailed.mkString(", ")}] non fanno parte degli IBAN validi per la PA"
        log.warn(msg)
        Failure(exception.DigitPaException(msg, DigitPaErrorCodes.PPT_IBAN_NON_CENSITO, throwable.cause))
      }
    } else {
      Success(true)
    }
  }

  def checkUrlStazione(redirectProtocollo: Option[String], redirectIp: Option[String], redirectPorta: Option[Long], redirectPath: Option[String]): Try[Boolean] = {

    val result = if (redirectProtocollo.isEmpty) {
      Failure(exception.DigitPaException("Errore Protocollo di redirezione Stazione non valorizzato", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else if (!("HTTP" == redirectProtocollo.get || "HTTPS" == redirectProtocollo.get)) {
      Failure(
        exception.DigitPaException(s"Errore Protocollo di redirezione Stazione non valorizzato correttamente, redirectProtocollo: [${redirectProtocollo.get}]", DigitPaErrorCodes.PPT_AUTORIZZAZIONE)
      )
    } else if (redirectIp.isEmpty) {
      Failure(exception.DigitPaException("Errore Ip di redirezione Stazione non valorizzato", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else if (redirectPorta.isEmpty) {
      Failure(exception.DigitPaException("Errore Porta di redirezione Stazione non valorizzata", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else if (redirectPath.isEmpty) {
      Failure(exception.DigitPaException("Errore Path di redirezione Stazione non valorizzato", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else {
      Success(true)
    }

    result
  }

  private def checkUrlCanale(redirectProtocollo: Option[String], redirectIp: Option[String], redirectPorta: Option[Long], redirectPath: Option[String]): Try[Boolean] = {
    val result = if (redirectProtocollo.isEmpty) {
      Failure(exception.DigitPaException("Errore Protocollo di redirezione Canale non valorizzato", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    } else if (!("HTTP" == redirectProtocollo.get || "HTTPS" == redirectProtocollo.get)) {
      Failure(exception.DigitPaException(s"Errore Protocollo di redirezione Canale non valorizzato correttamente, redirectProtocollo: [${redirectProtocollo.get}]", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    } else if (redirectIp.isEmpty) {
      Failure(exception.DigitPaException("Errore Ip di redirezione Canale non valorizzato", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    } else if (redirectPorta.isEmpty) {
      Failure(exception.DigitPaException("Errore Porta di redirezione Canale non valorizzata", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    } else if (redirectPath.isEmpty) {
      Failure(exception.DigitPaException("Errore Path di redirezione Canale non valorizzato", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    } else {
      Success(true)
    }

    if (result.isFailure) log.warn(result.failed.get, result.failed.get.getMessage)

    result
  }

}
