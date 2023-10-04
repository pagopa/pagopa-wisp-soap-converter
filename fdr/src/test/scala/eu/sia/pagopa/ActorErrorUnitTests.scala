package eu.sia.pagopa

import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.rendicontazioni.actor.rest.GetAllRevisionFdrActorPerRequest

import scala.concurrent.Promise

//class NotifyFlussoRendicontazioneActorPerRequestTest(testPromise: Promise[Boolean], override val repositories: Repositories, override val actorProps: ActorProps)
//    extends NotifyFlussoRendicontazioneActorPerRequest(repositories, actorProps) {
//
//  override def complete(fn: () => Unit): Unit = {
//    testPromise.success(true)
//    super.complete(fn)
//  }
//}

class GetAllRevisionFdrActorPerRequestTest(testPromise: Promise[Boolean], override val repositories: Repositories, override val actorProps: ActorProps)
    extends GetAllRevisionFdrActorPerRequest(repositories, actorProps) {

  override def complete(fn: () => Unit): Unit = {
    testPromise.success(true)
    super.complete(fn)
  }
}
