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

trait NodoInviaRPTResponse { this: NodoLogging =>

  final val NODO_INVIA_RPT_RISPOSTA = "nodoInviaRPTRisposta"

  def actorError(replyTo: ActorRef, req: SoapRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val fbe = RptFaultBeanException(dpe)
    val response = errorHandler(fbe)
    replyTo ! SoapResponse(req.sessionId, response._1, 200, re, req.testCaseId, Some(fbe))
  }

  def createNodoInviaRPTRisposta(
      outputXsdValid: Boolean,
      url: Option[String],
      redirect: Option[Int],
      esitoResponse: Boolean,
      faultBeanException: Option[RptFaultBeanException]
  ): Try[(String, NodoInviaRPTRisposta)] = {
    val esitoComplessivoOperazione = if (esitoResponse) Constant.OK else Constant.KO
    val redirectopt = if (esitoResponse) redirect else None
    val urlChecked = if (esitoResponse) url else None
    val inviaRPTRisposta =
      generateNodoInviaRPTRisposta(urlChecked, faultBeanException, esitoComplessivoOperazione, redirectopt)

    (for {
      payloadInviaRPTRisposta <- generateXml(inviaRPTRisposta)
      _ = log.info(LogConstant.logSintattico(NODO_INVIA_RPT_RISPOSTA))
      _ <- XsdValid.checkOnly(payloadInviaRPTRisposta, XmlEnum.NODO_INVIA_RPT_RISPOSTA_NODOPERPA, outputXsdValid)
    } yield (payloadInviaRPTRisposta, inviaRPTRisposta)).recoverWith({ case e =>
      log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
      val cfb = RptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
      Failure(cfb)
    })
  }

  def errorHandler(faultBeanException: RptFaultBeanException): (String, NodoInviaRPTRisposta) = {
    log.info(LogConstant.logGeneraPayload(NODO_INVIA_RPT_RISPOSTA))
    val inviaRPTRisposta = generateNodoInviaRPTRisposta(None, Some(faultBeanException), Constant.KO, None)
    val payloadInviaRPTRisposta = XmlEnum.nodoInviaRPTRisposta2Str_nodoperpa(inviaRPTRisposta).get

    log.info(LogConstant.logSintattico(NODO_INVIA_RPT_RISPOSTA))
    XsdValid.checkOnly(payloadInviaRPTRisposta, XmlEnum.NODO_INVIA_RPT_RISPOSTA_NODOPERPA) recover { case e =>
      log.warn(s"il payload nodoInviaRTRisposta generato non ha passato validazione sintattica. Risponderà comunque il payload. [${e.getMessage}]")
    }
    payloadInviaRPTRisposta -> inviaRPTRisposta
  }

  private def generateNodoInviaRPTRisposta(url: Option[String], e: Option[RptFaultBeanException], esitoComplessivoOperazione: String, redirect: Option[Int]): NodoInviaRPTRisposta = {
    NodoInviaRPTRisposta(
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
    XmlEnum.nodoInviaRPTRisposta2Str_nodoperpa(response)
  }
}
