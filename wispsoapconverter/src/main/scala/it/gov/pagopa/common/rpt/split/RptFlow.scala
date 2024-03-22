package it.gov.pagopa.common.rpt.split

import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.config._
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import scalaxb.Base64Binary
import scalaxbmodel.nodoperpa.{IntestazionePPT, NodoInviaRPT}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import scala.util.{Failure, Success, Try}

trait RptFlow extends CommonRptCheck with ValidateRpt with ReUtil { this: PerRequestActor =>

  def parseInput(payload: String, inputXsdValid: Boolean): Try[(IntestazionePPT, NodoInviaRPT)] = {
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_INVIA_RPT_NODOPERPA, inputXsdValid)
      (header, body) <- XmlEnum.str2nodoInviaRPTWithHeader_nodoperpa(payload)
    } yield (header, body)) recoverWith { case e =>
      log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
      val cfb = RptFaultBeanException(exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e))
      Failure(cfb)
    }
  }

  def parseRpt(rptEncoded: Base64Binary, inputXsdValid: Boolean, checkUTF8: Boolean): Try[(CtRichiestaPagamentoTelematico, String)] = {
    (for {
      rptDecoded <- StringUtils.getStringDecoded(rptEncoded, checkUTF8)
      _ <- XsdValid.checkOnly(rptDecoded, XmlEnum.RPT_PAGINF, inputXsdValid)
      body <- XmlEnum.str2RPT_paginf(rptDecoded)
    } yield (body, rptDecoded)) recoverWith {
      case e: java.nio.charset.MalformedInputException =>
        log.warn(e, RptHelperLog.XSD_KO(s"Data encoding is not ${Constant.UTF_8}"))
        val cfb = RptFaultBeanException(exception.DigitPaException(s"Data encoding is not ${Constant.UTF_8}", DigitPaErrorCodes.PPT_SINTASSI_XSD, e))
        Failure(cfb)
      case e =>
        log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
        val cfb = RptFaultBeanException(exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, e))
        Failure(cfb)
    }
  }

  def validateInput(ddataMap: ConfigData, rptKey: RPTKey, nodoInviaRpt: NodoInviaRPT, header: IntestazionePPT, rpt: CtRichiestaPagamentoTelematico): Try[Option[PaymentServiceProvider]] = {
    val check = for {
      (_, _, staz) <- DDataChecks.checkPaIntermediarioPaStazione(
        log,
        ddataMap,
        header.identificativoDominio,
        header.identificativoIntermediarioPA,
        header.identificativoStazioneIntermediarioPA,
        None,
        Some(nodoInviaRpt.password)
      )
      (psp, _, canale) <- DDataChecks.checkPspIntermediarioPspCanale(
        log,
        ddataMap,
        Some(nodoInviaRpt.identificativoPSP),
        nodoInviaRpt.identificativoIntermediarioPSP,
        Some(nodoInviaRpt.identificativoCanale),
        None,
        Some(rpt.datiVersamento.tipoVersamento.toString)
      )
      _ <-
        if (canale.isDefined && ModelloPagamento.ATTIVATO_PRESSO_PSP.toString != canale.get.paymentModel) {
          checkUrlStazione(staz.redirect.protocol, staz.redirect.ip, staz.redirect.port, staz.redirect.path)
        } else {
          Success(())
        }
      _ <- checkPluginRedirectCanale(ddataMap, nodoInviaRpt.identificativoCanale)
    } yield psp

    check recoverWith {
      case digitPaException: DigitPaException =>
        val cfb = RptFaultBeanException(digitPaException = digitPaException, rptKey = Some(rptKey), workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO))
        Failure(cfb)

      case ex: Throwable =>
        log.warn(ex, s"Errore generico durante la validazione dell'input, message: [${ex.getMessage}]")
        val cfb = RptFaultBeanException(
          digitPaException = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, ex),
          rptKey = Some(rptKey),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO)
        )
        Failure(cfb)
    }
  }

  def validateRpt(ddataMap: ConfigData, rptKey: RPTKey, nodoInviaRPT: NodoInviaRPT, intestazionePPT: IntestazionePPT, ctRPT: CtRichiestaPagamentoTelematico, isBolloEnabled: Boolean): Try[Boolean] = {
    val check: Try[Boolean] = for {
      _ <- checkDatiElementoListaRpt(intestazionePPT.identificativoUnivocoVersamento, intestazionePPT.codiceContestoPagamento, intestazionePPT.identificativoDominio, ctRPT)
      _ <- validate(ddataMap, intestazionePPT.identificativoDominio, nodoInviaRPT.identificativoPSP, ctRPT, nodoInviaRPT.identificativoCanale, isBolloEnabled)
      result = true
    } yield result

    check recoverWith {
      case digitPaException: DigitPaException =>
        val cfb = RptFaultBeanException(digitPaException = digitPaException, rptKey = Some(rptKey), workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO))
        Failure(cfb)

      case ex: Throwable =>
        log.warn(ex, s"Errore generico durante la validazione dell'RPT, message: [${ex.getMessage}]")
        val cfb = RptFaultBeanException(
          digitPaException = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, ex),
          rptKey = Some(rptKey),
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO)
        )
        Failure(cfb)
    }
  }

}
