package it.gov.pagopa.tests

import akka.actor.Props
import it.gov.pagopa.common.message.{ReExtra, SoapRequest, SoapResponse}
import it.gov.pagopa.common.util.{Primitive, Util}
import it.gov.pagopa.tests.testutil.TestItems

import java.util.UUID

//@org.scalatest.Ignore
class BadRequestsUnitTests() extends BaseUnitTest {

  "all bad requests soap" must {
    Primitive.soap.foreach(s => {
      val prim = s._1
      val c = s._2._2.apply(false)
      c.getSimpleName in {
        val act = system.actorOf(Props.create(c,cosmosRepository, props.copy(actorClassId = c.getSimpleName.replace("ActorPerRequest", ""))), s"${c.getSimpleName}${Util.now()}")
        act ! SoapRequest(UUID.randomUUID().toString, "bad request", TestItems.testPDD, prim, "test", Util.now(), ReExtra(), false, None)
        val res = expectMsgType[SoapResponse](singletesttimeout)
        assert(res.payload.contains("PPT_SINTASSI_EXTRAXSD"))
      }
    })
  }
}
