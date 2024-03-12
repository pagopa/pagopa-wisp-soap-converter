package it.gov.pagopa.actors.flow

import it.gov.pagopa
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.xml.{XmlUtil, XsdValid}
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.config.{BrokerCreditorInstitution, Station}
import it.gov.pagopa.exception.{CarrelloRptFaultBeanException, RptFaultBeanException, WorkflowExceptionErrorCodes}
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, NodoInviaCarrelloRPT, TipoElementoListaRPT}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import scala.collection.mutable.{Map => MutMap}
import scala.util.{Failure, Success, Try}

trait CarrelloFlow extends CommonRptCheck with ValidateRpt with ReUtil { this: PerRequestActor =>

  def parseCarrello(payload: String, inputXsdValid: Boolean): Try[(IntestazioneCarrelloPPT, NodoInviaCarrelloRPT)] = {
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_INVIA_CARRELLO_RPT_NODOPERPA, inputXsdValid)
      (header, body) <- XmlEnum.str2nodoInviaCarrelloRPTWithHeader_nodoperpa(payload)
    } yield (header, body)) recoverWith { case e =>
      log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
      val cfb = CarrelloRptFaultBeanException(DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e), idCanale = None)
      Failure(cfb)
    }
  }

  def validCarrello(
      ddataMap: ConfigData,
      maxNumRptInCart: Int,
      rptKeys: Seq[RPTKey],
      intestazioneCarrelloPPT: IntestazioneCarrelloPPT,
      nodoInviaCarrelloRPT: NodoInviaCarrelloRPT,
      multibeneficiario: Boolean,
      idCanaleAgid: String,
      idPspAgid: String,
      idIntPspAgid: String
  ): Try[(BrokerCreditorInstitution, Station)] = {
    val check = for {
      _ <- Success(())
      _ <- DDataChecks.checkPspIntermediarioPspCanale(
        log,
        ddataMap,
        Some(nodoInviaCarrelloRPT.identificativoPSP),
        nodoInviaCarrelloRPT.identificativoIntermediarioPSP,
        Some(nodoInviaCarrelloRPT.identificativoCanale)
      )
      _ <- checkCanaleModello(ddataMap, nodoInviaCarrelloRPT.identificativoCanale)

      (intermediarioPa, stazione) <-
        if (!multibeneficiario) {
          DDataChecks.checkIntermediarioPaStazionePassword(
            log,
            ddataMap,
            intestazioneCarrelloPPT.identificativoIntermediarioPA,
            intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA,
            nodoInviaCarrelloRPT.password
          )
        } else {
          DDataChecks.checkIntermediarioPaStazionePasswordMultibeneficiario(
            log,
            ddataMap,
            intestazioneCarrelloPPT.identificativoIntermediarioPA,
            intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA,
            nodoInviaCarrelloRPT.password
          )
        }
      _ <-
        if (
          multibeneficiario && (nodoInviaCarrelloRPT.identificativoPSP != idPspAgid || nodoInviaCarrelloRPT.identificativoIntermediarioPSP != idIntPspAgid || nodoInviaCarrelloRPT.identificativoCanale != idCanaleAgid)
        ) {
          Failure(exception.DigitPaException("Flag multibeneficiario non disponibile per pagamenti diversi da WISP2", DigitPaErrorCodes.PPT_SEMANTICA))
        } else {
          Success(())
        }
      _ <- checkUrlStazione(stazione.redirect.protocol, stazione.redirect.ip, stazione.redirect.port, stazione.redirect.path)
      _ <- checkPluginRedirectCanale(ddataMap, nodoInviaCarrelloRPT.identificativoCanale)
      _ <- checkRptNumbers(maxNumRptInCart, nodoInviaCarrelloRPT.listaRPT.elementoListaRPT)
      _ <- checkDuplicatoNelloStessoCarrello(nodoInviaCarrelloRPT.listaRPT.elementoListaRPT)
      _ <- codiceVersantePagatoreNonCoerente(nodoInviaCarrelloRPT.listaRPT.elementoListaRPT)
    } yield (intermediarioPa, stazione)

    check recoverWith {
      case digitPaException: DigitPaException =>
        val cfb = CarrelloRptFaultBeanException(
          digitPaException = digitPaException,
          idCarrello = Some(intestazioneCarrelloPPT.identificativoCarrello),
          rptKeys = Some(rptKeys),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO),
          idCanale = Some(nodoInviaCarrelloRPT.identificativoCanale)
        )
        Failure(cfb)

      case ex: Throwable =>
        log.warn(ex, s"Errore generico durante la validazione del carrello, message: [${ex.getMessage}]")
        val cfb = CarrelloRptFaultBeanException(
          digitPaException = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, ex),
          idCarrello = Some(intestazioneCarrelloPPT.identificativoCarrello),
          rptKeys = Some(rptKeys),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO),
          idCanale = Some(nodoInviaCarrelloRPT.identificativoCanale)
        )
        Failure(cfb)
    }
  }

  def validCarrelloMultibeneficiario(ddataMap: ConfigData, maxNumRptInCart: Int, intestazioneCarrelloPPT: IntestazioneCarrelloPPT, nodoInviaCarrelloRPT: NodoInviaCarrelloRPT): Try[(String, String)] = {
    val check: Try[(String, String)] = for {
      _ <-
        if (intestazioneCarrelloPPT.identificativoCarrello.matches("[a-zA-Z0-9]{11}[a-zA-Z0-9]{18}-[0-9]{5}")) {
          Success(())
        } else {
          Failure(exception.DigitPaException(s"L'idCarrello deve essere composto nella seguente forma: <idDominio(11)><numeroAvviso(18)><-><Progressivo(5)>", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
        }

      idDominio = intestazioneCarrelloPPT.identificativoCarrello.substring(0, 11)
      noticeNumber = intestazioneCarrelloPPT.identificativoCarrello.substring(11, 29)

      _ <- DDataChecks.checkPA(log, ddataMap, idDominio)
      //Il numero Avviso deve contenere la stazione versione 2
      (_, staz, _, _) <- DDataChecks.checkPaStazionePa(log, ddataMap, idDominio, noticeNumber)
      _ <-
        if (staz.version == 1) {
          Failure(exception.DigitPaException("Il numero Avviso deve contenere la stazione versione 2", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
        } else {
          Success(())
        }

      //Il carrello contiene solo 2 RPT
      _ <-
        if (nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.size == maxNumRptInCart) {
          Success(())
        } else {
          Failure(exception.DigitPaException(s"Il carrello non contiene solo $maxNumRptInCart RPT", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
        }

    } yield (idDominio, noticeNumber)

    check recoverWith {
      case digitPaException: DigitPaException =>
        val cfb = CarrelloRptFaultBeanException(
          digitPaException = digitPaException,
          idCarrello = Some(intestazioneCarrelloPPT.identificativoCarrello),
          rptKeys = Some(nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.map(e => RPTKey(e.identificativoDominio, e.identificativoUnivocoVersamento, e.codiceContestoPagamento))),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO),
          idCanale = Some(nodoInviaCarrelloRPT.identificativoCanale)
        )
        Failure(cfb)

      case ex: Throwable =>
        log.warn(ex, s"Errore generico durante la validazione del carrello, message: [${ex.getMessage}]")
        val cfb = CarrelloRptFaultBeanException(
          digitPaException = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, ex),
          idCarrello = Some(intestazioneCarrelloPPT.identificativoCarrello),
          rptKeys = Some(nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.map(e => RPTKey(e.identificativoDominio, e.identificativoUnivocoVersamento, e.codiceContestoPagamento))),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO),
          idCanale = Some(nodoInviaCarrelloRPT.identificativoCanale)
        )
        Failure(cfb)
    }
  }

  def validRpts(
      ddataMap: ConfigData,
      idCarrello: String,
      rptKeys: Seq[RPTKey],
      nodoInviaCarrelloRPT: NodoInviaCarrelloRPT,
      intestazioneCarrelloPPT: IntestazioneCarrelloPPT,
      isBolloEnabled: Boolean,
      maxVersamentiInSecondRpt: Int
  ): Try[Boolean] = {

    val errorMap: MutMap[Int, RptFaultBeanException] = MutMap.empty

    val primarpt: CtRichiestaPagamentoTelematico =
      XmlEnum.str2RPT_paginf(XmlUtil.StringBase64Binary.decodeBase64(nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.head.rpt)).get

    nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.zipWithIndex.map { case (r, index) =>
      val rpt = XmlEnum.str2RPT_paginf(XmlUtil.StringBase64Binary.decodeBase64(r.rpt)).get
      val idCanale = nodoInviaCarrelloRPT.identificativoCanale
      val idPSP = nodoInviaCarrelloRPT.identificativoPSP
      val idIntPSP = nodoInviaCarrelloRPT.identificativoIntermediarioPSP

      val check: Try[Boolean] = for {
        _ <- checkDatiElementoListaRpt(r.identificativoUnivocoVersamento, r.codiceContestoPagamento, r.identificativoDominio, rpt)
        _ <- DDataChecks.checkPspIntermediarioPspCanale(log, ddataMap, Some(idPSP), idIntPSP, Some(idCanale), None, Some(rpt.datiVersamento.tipoVersamento.toString))
        _ <-
          if (nodoInviaCarrelloRPT.multiBeneficiario.contains(true)) {

            val idDominioMulti = intestazioneCarrelloPPT.identificativoCarrello.substring(0, 11)

            (if (index == 0) {
               for {
                 _ <- DDataChecks.checkPaIntermediarioPaStazioneMultibeneficiario(
                   log,
                   ddataMap,
                   rpt.dominio.identificativoDominio,
                   intestazioneCarrelloPPT.identificativoIntermediarioPA,
                   intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA
                 )
                 //L’idCarrello  deve essere composto nella seguente forma: <idDominio(11)><numeroAvviso(18)><-><Progressivo(5)>
                 _ <-
                   if (idDominioMulti != rpt.dominio.identificativoDominio) {
                     Failure(exception.DigitPaException("L’idCarrello deve contenere idDominio e corrispondere al dominio della prima rpt", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   } else {
                     Success(())
                   }

               } yield ()

             } else {
               for {
                 _ <- DDataChecks.checkPA(log, ddataMap, rpt.dominio.identificativoDominio)

                 //La seconda RPT contiene solo 1 versamento
                 _ <-
                   if (rpt.datiVersamento.datiSingoloVersamento.size == maxVersamentiInSecondRpt) {
                     Success(())
                   } else {
                     Failure(exception.DigitPaException(s"La seconda RPT non contiene solo $maxVersamentiInSecondRpt versamento", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   }
                 //Lo stesso Ente deve essere presente con una sola RPT
                 _ <-
                   if (rpt.dominio.identificativoDominio != primarpt.dominio.identificativoDominio) {
                     Success(())
                   } else {
                     Failure(exception.DigitPaException("Lo stesso Ente deve essere presente con una sola RPT", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   }
                 //Lo IUV è identico per ogni RPT
                 _ <-
                   if (rpt.datiVersamento.identificativoUnivocoVersamento == primarpt.datiVersamento.identificativoUnivocoVersamento) {
                     Success(())
                   } else {
                     Failure(exception.DigitPaException("Lo IUV non è identico per ogni RPT", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   }
                 //Il dato dataEsecuzionePagamento è il medesimo per tutte le RPT
                 _ <-
                   if (rpt.datiVersamento.dataEsecuzionePagamento == primarpt.datiVersamento.dataEsecuzionePagamento) {
                     Success(())
                   } else {
                     Failure(exception.DigitPaException("Il dato dataEsecuzionePagamento non è il medesimo per tutte le RPT", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   }
                 //Il carrello deve avere massimo 5 versamenti totali ( tra le RPT )
                 _ <-
                   if (rpt.datiVersamento.datiSingoloVersamento.size + primarpt.datiVersamento.datiSingoloVersamento.size > 5) {
                     Failure(exception.DigitPaException("Il carrello deve avere massimo 5 versamenti totali", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                   } else {
                     Success(())
                   }
               } yield ()
             }).flatMap(_ => {
              for {
                //Il CCP delle RPT devono contenere l’idCarrello
                _ <-
                  if (rpt.datiVersamento.codiceContestoPagamento != intestazioneCarrelloPPT.identificativoCarrello) {
                    Failure(exception.DigitPaException("I CCP delle RPT devono contenere l’idCarrello", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                  } else {
                    Success(())
                  }
                //Ogni RPT contiene solo iban dell’Ente riferito all’interno della RPT
                _ <- Try(
                  rpt.datiVersamento.datiSingoloVersamento.map(dsv =>
                    dsv.ibanAccredito.map(s => {
                      DDataChecks
                        .checkIban(log, ddataMap, rpt.dominio.identificativoDominio, s)
                        .recoverWith({ case e =>
                          Failure(exception.DigitPaException("Ogni RPT deve contenere solo iban dell’Ente riferito all’interno della RPT", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO, e))
                        })
                        .get
                    })
                  )
                )
                //Nessuna RPT contiene marca da bollo
                _ <-
                  if (rpt.datiVersamento.datiSingoloVersamento.exists(_.datiMarcaBolloDigitale.nonEmpty)) {
                    Failure(exception.DigitPaException("Nessuna RPT deve contienere marca da bollo", DigitPaErrorCodes.PPT_MULTI_BENEFICIARIO))
                  } else {
                    Success(())
                  }
              } yield ()
            })
          } else {
            DDataChecks.checkPaIntermediarioPaStazione(
              log,
              ddataMap,
              r.identificativoDominio,
              intestazioneCarrelloPPT.identificativoIntermediarioPA,
              intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA
            )
          }

        _ <- validate(ddataMap, r.identificativoDominio, idPSP, rpt, idCanale, isBolloEnabled)
        result = true
      } yield result
      check recoverWith {
        case digitPaException: DigitPaException =>
          val cfb = pagopa.exception.RptFaultBeanException(
            digitPaException = digitPaException,
            rptKey = Option(RPTKey(r.identificativoDominio, r.identificativoUnivocoVersamento, r.codiceContestoPagamento)),
            workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO)
          )
          errorMap += index + 1 -> cfb
          Failure(cfb)

        case ex: Throwable =>
          log.warn(
            ex,
            s"Errore generico durante la validazione dell'RPT, message: [${ex.getMessage}], idDominio: [${rpt.dominio.identificativoDominio}], iuv: [${rpt.datiVersamento.identificativoUnivocoVersamento}], ccp: [${rpt.datiVersamento.codiceContestoPagamento}]"
          )
          val cfb = pagopa.exception.RptFaultBeanException(
            digitPaException = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, ex),
            rptKey = Option(RPTKey(r.identificativoDominio, r.identificativoUnivocoVersamento, r.codiceContestoPagamento)),
            workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO)
          )
          errorMap += index + 1 -> cfb
          Failure(cfb)
      }
    }

    if (errorMap.isEmpty) {
      Success(true)
    } else {
      val cfb =
        CarrelloRptFaultBeanException(
          digitPaException = exception.DigitPaException("Errore in una o piu' RPT del carrello", DigitPaErrorCodes.PPT_SEMANTICA),
          detailErrors = Some(errorMap.toSeq.sortBy(_._1)),
          idCarrello = Some(idCarrello),
          rptKeys = Option(rptKeys),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO),
          idCanale = Some(nodoInviaCarrelloRPT.identificativoCanale)
        )
      Failure(cfb)
    }
  }

  def parseRpts(idCanale: String, inputXsdValid: Boolean, rpts: Seq[TipoElementoListaRPT], _idCarrello: String, rptKeys: Seq[RPTKey], checkUTF8: Boolean): Try[Seq[CtRichiestaPagamentoTelematico]] = {
    val errorMap: MutMap[Int, RptFaultBeanException] = MutMap.empty

    val res: Seq[Try[CtRichiestaPagamentoTelematico]] = rpts.zipWithIndex.map { case (r, index) =>
      (for {
        b64 <- StringUtils.getStringDecoded(r.rpt, checkUTF8)
        _ <- XsdValid.checkOnly(b64, XmlEnum.RPT_PAGINF, inputXsdValid)
        body <- XmlEnum.str2RPT_paginf(XmlUtil.StringBase64Binary.decodeBase64(r.rpt))
      } yield body) recoverWith {
        case e: java.nio.charset.MalformedInputException =>
          log.warn(e, RptHelperLog.XSD_KO(s"Data encoding is not ${Constant.UTF_8}"))
          val cfb = pagopa.exception.RptFaultBeanException(
            digitPaException = exception.DigitPaException(s"Data encoding is not ${Constant.UTF_8}", DigitPaErrorCodes.PPT_SINTASSI_XSD, e),
            rptKey = Option(RPTKey(r.identificativoDominio, r.identificativoUnivocoVersamento, r.codiceContestoPagamento))
          )
          errorMap += index + 1 -> cfb
          Failure(cfb)
        case e =>
          log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
          val cfb = pagopa.exception.RptFaultBeanException(
            digitPaException = exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, e),
            rptKey = Option(RPTKey(r.identificativoDominio, r.identificativoUnivocoVersamento, r.codiceContestoPagamento))
          )
          errorMap += index + 1 -> cfb
          Failure(cfb)
      }
    }

    if (errorMap.isEmpty) {
      Success(res.map(_.get))
    } else {
      val cfb =
        CarrelloRptFaultBeanException(
          digitPaException = exception.DigitPaException("Errore in una o piu' RPT del carrello", DigitPaErrorCodes.PPT_SINTASSI_XSD),
          idCarrello = Some(_idCarrello),
          rptKeys = Some(rptKeys),
          detailErrors = Some(errorMap.toSeq.sortBy(_._1)),
          idCanale = Some(idCanale)
        )
      Failure(cfb)
    }
  }

}
