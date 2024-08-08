package it.gov.pagopa.actors

import akka.actor.ActorRef
import it.gov.pagopa.ActorProps
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.exception.DigitPaException
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.CosmosRepository

import scala.xml.XML

case class NodoChiediCopiaRTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor {
  var req: SoapRequest = _
  var replyTo: ActorRef = _

  override def receive: Receive = {
    case soapRequest: SoapRequest =>
      log.info("NodoChiediCopiaRT received - " + soapRequest.payload)
      req = soapRequest
      replyTo = sender()

      // Extract values from SOAP request
      val xml = XML.loadString(soapRequest.payload)
      val ccp = (xml \\ "codiceContestoPagamento").text
      val iuv = (xml \\ "identificativoUnivocoVersamento").text
      val id = (xml \\ "identificativoDominio").text

      // Concatenate values and get rptKey
      val rtKey = s"${ccp}_${iuv}_${id}"
      log.info(s"Get RT for the key: $rtKey")

      cosmosRepository.getRtByKey(rtKey).foreach {
        case Some(rtEntity) =>
          log.info(s"Found entity: $rtEntity")
          replyTo ! rtEntity
        case None =>
          log.info("No item found with the given key")
          replyTo ! None
      }
  }

  override def actorError(e: DigitPaException): Unit = {
    log.error(e, "Error in NodoChiediCopiaRTActorPerRequest")
  }
}