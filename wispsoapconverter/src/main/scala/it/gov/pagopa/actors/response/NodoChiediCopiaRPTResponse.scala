package it.gov.pagopa.actors.response

import akka.actor.ActorRef
import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message.{Re, SoapRequest, SoapResponse}
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.exception.RptFaultBeanException
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{FaultBean, NodoInviaRPTRisposta}

import scala.util.{Failure, Try}

trait NodoChiediCopiaRPTResponse { this: NodoLogging =>

  final val NODO_CHIEDI_COPIA_RPT_RISPOSTA = "nodoInviaRPTRisposta"

  def actorError(replyTo: ActorRef, req: SoapRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val fbe = RptFaultBeanException(dpe)
    val response = errorHandler(fbe)
    replyTo ! SoapResponse(req.sessionId, response._1, 200, re, req.testCaseId, Some(fbe))
  }

  def createNodoChiediCopiaRPTRisposta(
      outputXsdValid: Boolean,
      url: Option[String],
      redirect: Option[Int],
      esitoResponse: Boolean,
      faultBeanException: Option[RptFaultBeanException]
  ): Try[(String, NodoInviaRPTRisposta)] = {
    val esitoComplessivoOperazione = if (esitoResponse) Constant.OK else Constant.KO
    val redirectopt = if (esitoResponse) redirect else None
    val urlChecked = if (esitoResponse) url else None
    val chiediCopiaRPTRisposta =
      generateNodoChiediCopiaRPTRisposta(urlChecked, faultBeanException, esitoComplessivoOperazione, redirectopt)

    (for {
      payloadChiediCopiaRPTRisposta <- generateXml(inviaRPTRisposta)
      _ = log.info(LogConstant.logSintattico(NODO_CHIEDI_COPIA_RPT_RISPOSTA))
      _ <- XsdValid.checkOnly(payloadChiediCopiaRPTRisposta, XmlEnum.NODO_CHIEDI_COPIA_RT_NODOPERPA, outputXsdValid)
    } yield (payloadChiediCopiaRPTRisposta, chiediCopiaRPTRisposta)).recoverWith({ case e =>
      log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
      val cfb = RptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
      Failure(cfb)
    })
  }

  def errorHandler(faultBeanException: RptFaultBeanException): (String, NodoChiediCopiaRPTRisposta) = {
    log.info(LogConstant.logGeneraPayload(NODO_CHIEDI_COPIA_RPT_RISPOSTA))
    val chiediCopiaRPTRisposta = generateNodoChiediCopiaRPTRisposta(None, Some(faultBeanException), Constant.KO, None)
    val payloadChiediCopiaRPTRisposta = XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(chiediCopiaRPTRisposta).get

    log.info(LogConstant.logSintattico(NODO_CHIEDI_COPIA_RPT_RISPOSTA))
    XsdValid.checkOnly(payloadChiediCopiaRPTRisposta, XmlEnum.NODO_CHIEDI_COPIA_RT_NODOPERPA) recover { case e =>
      log.warn(s"il payload nodoChiediCopiaRTRisposta generato non ha passato validazione sintattica. Risponderà comunque il payload. [${e.getMessage}]")
    }
    payloadChiediCopiaRPTRisposta -> chiediCopiaRPTRisposta
  }

  private def generateNodoChiediCopiaRPTRisposta(url: Option[String], e: Option[RptFaultBeanException], esitoComplessivoOperazione: String, redirect: Option[Int]): NodoInviaRPTRisposta = {
    NodoChiediCopiaRPTRisposta(
      e.map(ex => {
        FaultBean(
          ex.digitPaException.faultCode,
          ex.digitPaException.faultString,
          ex.digitPaException.reporterId,
          Some(ex.digitPaException.getMessage()),
          None,
          ex.digitPaException.originalFaultCode,
          ex.digitPaException.originalFaultString,
          ex.digitPaException.originalFaultDescription
        )
      }),
      esitoComplessivoOperazione,
      redirect,
      url
    )
  }

  private def generateXml(response: NodoInviaRPTRisposta): Try[String] = {
    XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(response)
  }
}
