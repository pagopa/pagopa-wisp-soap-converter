package eu.sia.pagopa

import akka.actor.Props
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.{Primitive, Util}
import eu.sia.pagopa.nodopoller.actor.PollerActor

import java.util.UUID

//@org.scalatest.Ignore
class PollerActorUnitTests() extends BaseUnitTest {

  val routers = Primitive.jobs.map(j => {
    BootstrapUtil.actorRouter(j._1) -> mockActor
  })
  val act =
    system.actorOf(Props.create(classOf[PollerActor], repositories, props.copy(actorClassId = "pollerActor", routers = routers)), s"pollerActor${Util.now()}")

  "pollerActor" must {
    Primitive.jobs.foreach(j => {
      j._1 in {
        act ! TriggerJobRequest(UUID.randomUUID().toString, j._1, None, None)
        expectMsgType[TriggerJobResponse](singletesttimeout)
      }
    })
  }
}
