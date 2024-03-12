package it.gov.pagopa.tests

import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.{NodoInviaCarrelloRPTActorPerRequest, NodoInviaRPTActorPerRequest}
import it.gov.pagopa.common.repo.CosmosRepository

import scala.concurrent.Promise

class NodoInviaRPTActorPerRequestTest(testPromise: Promise[Boolean], override val cosmosRepository:CosmosRepository, override val actorProps: ActorProps)
  extends NodoInviaRPTActorPerRequest(cosmosRepository, actorProps) {

  override def complete(fn: () => Unit): Unit = {
    testPromise.success(true)
    super.complete(fn)
  }
}

class NodoInviaCarrelloRPTTest(testPromise: Promise[Boolean], override val cosmosRepository:CosmosRepository, override val actorProps: ActorProps)
  extends NodoInviaCarrelloRPTActorPerRequest(cosmosRepository, actorProps) {
  override def complete(fn: () => Unit): Unit = {
    testPromise.success(true)
    super.complete(fn)
  }
}