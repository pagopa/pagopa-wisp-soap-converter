package eu.sia.pagopa

import akka.actor.Props
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.Util
import eu.sia.pagopa.ftpsender.actor.FtpRetryActorPerRequest

import java.util.UUID

//@org.scalatest.Ignore
class JobsUnitTests() extends BaseUnitTest {

  "ftpUpload" must {
    "ok" in {
      val act = system.actorOf(Props.create(classOf[FtpRetryActorPerRequest], repositories, props.copy(actorClassId = "ftpUpload")), s"ftpUpload${Util.now()}")
      act ! WorkRequest(UUID.randomUUID().toString, None, "", None, None)
      val res = expectMsgType[WorkResponse](singletesttimeout)
    }
  }

}
