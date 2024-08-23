package it.gov.pagopa.common.util

import it.gov.pagopa.actors.{NodoInviaCarrelloRPTActorPerRequest, NodoInviaRPTActorPerRequest}
import it.gov.pagopa.common.actor.PerRequestActor
import scalaxbmodel.nodoperpa.NodoChiediCopiaRT
import it.gov.pagopa.actors.NodoChiediCopiaRTActorPerRequest

object Primitive {

  val soap: Map[String, (String, Boolean => Class[_ <: PerRequestActor])] = Map(
    "nodoInviaRPT" -> ("Body/_/identificativoIntermediarioPA", _ => classOf[NodoInviaRPTActorPerRequest]),
    "nodoInviaCarrelloRPT" -> ("Header/_/identificativoStazioneIntermediarioPA", _ => classOf[NodoInviaCarrelloRPTActorPerRequest]),
    "nodoChiediCopiaRT" -> ("Body/_/identificativoStazioneIntermediarioPA", _ => classOf[NodoChiediCopiaRTActorPerRequest])
  )

  val allPrimitives = Primitive.soap

  def getActorClass(primitive: String, idempotency: Boolean): Class[_ <: PerRequestActor] = {
    allPrimitives(primitive)._2(idempotency)
  }
}
