package it.gov.pagopa.common.util

import akka.actor.Actor
import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.config.{Iban, PaymentServiceProvider, Plugin}
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import scalaxbmodel.nodoperpa.TipoElementoListaRPT
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import scala.annotation.tailrec
import scala.collection.immutable.HashSet
import scala.util.{Failure, Success, Try}

trait CommonRptCheck {
  this: Actor with NodoLogging =>

  def checkEmail(ctRPT: CtRichiestaPagamentoTelematico): Try[String] = {
    if (ctRPT.soggettoVersante.isDefined && ctRPT.soggettoVersante.get.eu45mailVersante.isDefined) {
      Success(ctRPT.soggettoVersante.get.eu45mailVersante.get)
    } else if (ctRPT.soggettoPagatore.eu45mailPagatore.isDefined) {
      Success(ctRPT.soggettoPagatore.eu45mailPagatore.get)
    } else {
      Success("")
    }
  }

  def checkCanaleModello(ddataMap: ConfigData, idCanale: String): Try[Boolean] = {
    val canale = DDataChecks.checkCanale(log, ddataMap, idCanale, None)
    if (canale.isSuccess && ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO.toString != canale.get.paymentModel && ModelloPagamento.DIFFERITO.toString != canale.get.paymentModel) {
      log.warn(
        s"Configurazione canale-modello pagamento non corretta, il modello pagamento dovrebbe essere IMMEDIATO_MULTIBENEFICIARIO o DIFFERITO, canale.modelloPagamento: [${canale.get.paymentModel}]"
      )
      Failure(exception.DigitPaException("Configurazione canale-modello pagamento non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else {
      Success(true)
    }
  }

  def checkRptNumbers(maxNumRptInCart: Int, rpts: Seq[TipoElementoListaRPT]): Try[Boolean] = {
    if (rpts.nonEmpty) {
      if (rpts.length > maxNumRptInCart) {
        log.warn(s"Numero di RPT in Carrello superiore al massimo consentito, rpts.length: [${rpts.length}], maxNumRptInCart: [$maxNumRptInCart]")
        Failure(exception.DigitPaException("Numero di RPT in Carrello superiore al massimo consentito", DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD))
      } else {
        Success(true)
      }
    } else {
      log.warn("Carrello vuoto")
      Failure(exception.DigitPaException("Carrello vuoto", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
    }
  }

  def checkDuplicatoNelloStessoCarrello(rpts: Seq[TipoElementoListaRPT]): Try[Boolean] = {
    @tailrec
    def rec(l: Seq[TipoElementoListaRPT], s: Set[String]): Option[String] = l match {
      case Nil => None
      case x :: xs =>
        val key =
          s"idDominio: [${x.identificativoDominio}], iuv: [${x.identificativoUnivocoVersamento}], ccp: [${x.codiceContestoPagamento}]"
        if (s(key)) Some(key) else rec(xs, s + key)
    }

    rec(rpts, new HashSet[String]) match {
      case Some(key) =>
        log.warn(s"Rpt duplicata nello stesso carrello, $key")
        Failure(exception.DigitPaException("Rpt duplicata nello stesso carrello", DigitPaErrorCodes.PPT_SEMANTICA))
      case None => Success(true)
    }
  }

  def codiceVersantePagatoreNonCoerente(rpts: Seq[TipoElementoListaRPT]): Try[Boolean] = {
    val versantePagatoreOption = rpts.headOption.map(firstRpt => {
      val rptTry = RPTUtil.getRptByBase64Binary(firstRpt.rpt)
      if (rptTry.isFailure) log.warn(s"La decodifica RPT non è andata a buon fine, rpt: [${firstRpt.rpt}]")
      val rpt = rptTry.get
      if (rpt.soggettoVersante.isDefined) {
        rpt.soggettoVersante.get.identificativoUnivocoVersante.codiceIdentificativoUnivoco
      } else {
        rpt.soggettoPagatore.identificativoUnivocoPagatore.codiceIdentificativoUnivoco
      }
    })
    versantePagatoreOption match {
      case Some(versantePagatore) =>
        val list = rpts.tail.map(otherRpt => {
          val rptTry = RPTUtil.getRptByBase64Binary(otherRpt.rpt)
          if (rptTry.isFailure) log.warn(s"La decodifica RPT non è andata a buon fine, rpt: [${otherRpt.rpt}]")
          val rpt = rptTry.get
          if (rpt.soggettoVersante.isDefined) {
            if (versantePagatore == rpt.soggettoVersante.get.identificativoUnivocoVersante.codiceIdentificativoUnivoco) {
              Success(true)
            } else {
              log.warn(
                s"Codice versante non coerente, versantePagatore: [$versantePagatore], rpt.soggettoVersante.identificativoUnivocoVersante.codiceIdentificativoUnivoco: [${rpt.soggettoVersante.get.identificativoUnivocoVersante.codiceIdentificativoUnivoco}]"
              )
              Failure(exception.DigitPaException("codice versante non coerente", DigitPaErrorCodes.PPT_SEMANTICA))
            }
          } else if (versantePagatore == rpt.soggettoPagatore.identificativoUnivocoPagatore.codiceIdentificativoUnivoco) {
            Success(true)
          } else {
            log.warn(
              s"Codice versante non coerente, versantePagatore: [$versantePagatore], rpt.soggettoPagatore.identificativoUnivocoPagatore.codiceIdentificativoUnivoco: [${rpt.soggettoPagatore.identificativoUnivocoPagatore.codiceIdentificativoUnivoco}]"
            )
            Failure(exception.DigitPaException("codice pagatore non coerente", DigitPaErrorCodes.PPT_SEMANTICA))
          }
        })
        if (list.exists(_.isFailure)) {
          Failure(list.find(_.isFailure).get.failed.get)
        } else {
          Success(true)
        }
      case None => Success(true)
    }
  }

}
