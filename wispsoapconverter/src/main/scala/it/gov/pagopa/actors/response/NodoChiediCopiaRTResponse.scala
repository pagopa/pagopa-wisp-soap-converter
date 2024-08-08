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
import scalaxbmodel.nodoperpa.{FaultBean, NodoChiediCopiaRTRisposta}

import scala.util.{Failure, Try}

trait NodoChiediCopiaRTResponse { this: NodoLogging =>

  final val NODO_CHIEDI_COPIA_RT_RISPOSTA = "nodoChiediCopiaRTRisposta"

  def actorError(replyTo: ActorRef, req: SoapRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val fbe = RptFaultBeanException(dpe)
    val response = errorHandler(fbe)
    replyTo ! SoapResponse(req.sessionId, response._1, 200, re, req.testCaseId, Some(fbe))
  }

  def createNodoChiediCopiaRTRisposta(
                                       outputXsdValid: Boolean,
                                       tipoFirma: String,
                                       rt: String,
                                       faultBeanException: Option[RptFaultBeanException]
                                     ): Try[(String, NodoChiediCopiaRTRisposta)] = {
    val nodoChiediCopiaRTRisposta = generateNodoChiediCopiaRTRisposta(tipoFirma, rt, faultBeanException)

    (for {
      payloadNodoChiediCopiaRTRisposta <- generateXml(nodoChiediCopiaRTRisposta)
      _ = log.info(LogConstant.logSintattico(NODO_CHIEDI_COPIA_RT_RISPOSTA))
      _ <- XsdValid.checkOnly(payloadNodoChiediCopiaRTRisposta, XmlEnum.NODO_CHIEDI_COPIA_RT_RISPOSTA_NODOPERPA, outputXsdValid)
    } yield (payloadNodoChiediCopiaRTRisposta, nodoChiediCopiaRTRisposta)).recoverWith({ case e =>
      log.warn(e, RptHelperLog.XSD_KO(e.getMessage))
      val cfb = RptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
      Failure(cfb)
    })
  }

  def errorHandler(faultBeanException: RptFaultBeanException): (String, NodoChiediCopiaRTRisposta) = {
    log.info(LogConstant.logGeneraPayload(NODO_CHIEDI_COPIA_RT_RISPOSTA))
    val nodoChiediCopiaRTRisposta = generateNodoChiediCopiaRTRisposta("ERROR", "", Some(faultBeanException))
    val payloadNodoChiediCopiaRTRisposta = XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(nodoChiediCopiaRTRisposta).get

    log.info(LogConstant.logSintattico(NODO_CHIEDI_COPIA_RT_RISPOSTA))
    XsdValid.checkOnly(payloadNodoChiediCopiaRTRisposta, XmlEnum.NODO_CHIEDI_COPIA_RT_RISPOSTA_NODOPERPA) recover { case e =>
      log.warn(s"Il payload nodoChiediCopiaRTRisposta generato non ha passato validazione sintattica. Risponderà comunque il payload. [${e.getMessage}]")
    }
    payloadNodoChiediCopiaRTRisposta -> nodoChiediCopiaRTRisposta
  }

  private def generateNodoChiediCopiaRTRisposta(tipoFirma: String, rt: String, e: Option[RptFaultBeanException]): NodoChiediCopiaRTRisposta = {
    NodoChiediCopiaRTRisposta(
      tipoFirma,
      rt
    )
  }

  private def generateXml(response: NodoChiediCopiaRTRisposta): Try[String] = {
    XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(response)
  }
}
