package eu.sia.pagopa.common.util

import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.ftpsender.actor.FtpRetryActorPerRequest
import eu.sia.pagopa.rendicontazioni.actor.{NodoChiediElencoFlussiRendicontazioneActorPerRequest, NodoChiediFlussoRendicontazioneActorPerRequest, NodoInviaFlussoRendicontazioneActorPerRequest}

object Primitive {

  val soap: Map[String, (String, Boolean => Class[_ <: PerRequestActor])] = Map(
    "nodoChiediFlussoRendicontazione" -> ("Body/_/identificativoStazioneIntermediarioPA", _ => classOf[NodoChiediFlussoRendicontazioneActorPerRequest]),
    "nodoInviaFlussoRendicontazione" -> ("Body/_/identificativoCanale", _ => classOf[NodoInviaFlussoRendicontazioneActorPerRequest]),
    "nodoChiediElencoFlussiRendicontazione" -> ("Body/_/identificativoStazioneIntermediarioPA", _ => classOf[NodoChiediElencoFlussiRendicontazioneActorPerRequest])
  )

  val jobs: Map[String, (String, Boolean => Class[_ <: PerRequestActor])] = Map(
    "ftpUpload" -> ("ftpUpload", _ => classOf[FtpRetryActorPerRequest])
  )

  val allPrimitives = Primitive.soap ++ Primitive.jobs

  def getActorClass(primitive: String): Class[_ <: PerRequestActor] = {
    allPrimitives(primitive)._2(false)
  }
}
