package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.response.NodoInviaRPTResponse
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.enums._
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.{CosmosPrimitive, CosmosRepository}
import it.gov.pagopa.common.rpt.split.RptFlow
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.{CategoriaEvento, Componente, Esito, SottoTipoEvento}
import it.gov.pagopa.config.Channel
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{IntestazionePPT, NodoInviaRPTRisposta}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import java.time.Instant
import java.util.Base64
import scala.concurrent.Future

case class NodoChiediCopiaRTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor {
  var req: SoapRequest = _
  var replyTo: ActorRef = _
  override def receive: Receive = {
    case soapRequest: SoapRequest =>
      req = soapRequest
      log.info(req.primitive)
      replyTo = sender()
  }

  override def actorError(e: DigitPaException): Unit = {
    log.error(e, "Error in NodoChiediCopiaRTActorPerRequest")
  }
}