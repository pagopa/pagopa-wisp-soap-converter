package it.gov.pagopa.actors.response

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.exception.DigitPaException
import it.gov.pagopa.common.message.{Re, SoapRequest, SoapResponse}
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.exception.CarrelloRptFaultBeanException
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{FaultBean, ListaErroriRPT, NodoInviaCarrelloRPTRisposta}

trait NodoInviaCarrelloRPTResponse { this: NodoLogging =>

  final val NODO_INVIA_CARRELLO_RPT_RISPOSTA = "nodoInviaCarrelloRPTRisposta"

  def actorError(replyTo: ActorRef, req: SoapRequest, idCanale: Option[String], dpe: DigitPaException, idCarrello: Option[String], rptKeys: Option[Seq[RPTKey]], re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val cfb = CarrelloRptFaultBeanException(dpe, idCarrello, rptKeys, idCanale = idCanale)
    val response = errorHandler(req.sessionId, req.testCaseId, cfb, re)
    //MDC.remove(Constant.MDCKey.SESSION_ID)
    replyTo ! response
  }

  def errorHandler(sessionId: String, testCaseId: Option[String], carrelloRptFaultBeanException: CarrelloRptFaultBeanException, re: Option[Re]): SoapResponse = {
    log.info(LogConstant.logGeneraPayload(NODO_INVIA_CARRELLO_RPT_RISPOSTA))
    val inviaCarrelloRPTRisposta =
      generateNodoInviaCarrelloRPTRisposta(None, Some(carrelloRptFaultBeanException), Constant.KO)
    val payloadInviaCarrelloRPTRisposta =
      XmlEnum.nodoInviaCarrelloRPTRisposta2Str_nodoperpa(inviaCarrelloRPTRisposta).get

    log.info(LogConstant.logSintattico(NODO_INVIA_CARRELLO_RPT_RISPOSTA))

    XsdValid.checkOnly(payloadInviaCarrelloRPTRisposta, XmlEnum.NODO_INVIA_CARRELLO_RPT_RISPOSTA_NODOPERPA) recover { case e =>
      log.warn(s"il payload nodoInviaCarrelloRTRisposta generato non ha passato validazione sintattica. Risponderà comunque il payload. [${e.getMessage}]")
    }
    SoapResponse(sessionId, payloadInviaCarrelloRPTRisposta, StatusCodes.OK.intValue, re, testCaseId, Some(carrelloRptFaultBeanException))

  }

  def generateNodoInviaCarrelloRPTRisposta(url: Option[String], e: Option[CarrelloRptFaultBeanException], esitoComplessivoOperazione: String): NodoInviaCarrelloRPTRisposta = {
    NodoInviaCarrelloRPTRisposta(
      e.flatMap(ex => {
        if (ex.detailErrors.isEmpty) {
          Some(
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
          )
        } else {
          None
        }
      }),
      esitoComplessivoOperazione,
      url,
      e.flatMap(ex => {
        ex.detailErrors.map(d => {
          ListaErroriRPT(d map { case (serial, faultBeanException) =>
            FaultBean(
              faultBeanException.digitPaException.faultCode,
              faultBeanException.digitPaException.faultString,
              faultBeanException.digitPaException.reporterId,
              Some(faultBeanException.digitPaException.getMessage()),
              Some(serial),
              ex.digitPaException.originalFaultCode,
              ex.digitPaException.originalFaultString,
              ex.digitPaException.originalFaultDescription
            )
          })
        })
      })
    )
  }

}
