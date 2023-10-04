package eu.sia.pagopa

import akka.actor.Props
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.DigitPaErrorCodes
import eu.sia.pagopa.common.message.{ReExtra, RestResponse, SoapRequest, SoapResponse, WorkRequest, WorkResponse}
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.util.Util
import eu.sia.pagopa.rendicontazioni.actor.rest.{GetAllRevisionFdrActorPerRequest, NotifyFlussoRendicontazioneActorPerRequest}
import eu.sia.pagopa.rendicontazioni.actor.soap.{NodoChiediElencoFlussiRendicontazioneActorPerRequest, NodoChiediFlussoRendicontazioneActorPerRequest, NodoInviaFlussoRendicontazioneActorPerRequest}

import scala.concurrent.Promise

class NotifyFlussoRendicontazioneTest(testPromise: Promise[Boolean], override val repositories: Repositories, override val actorProps: ActorProps)
    extends NotifyFlussoRendicontazioneActorPerRequest(repositories, actorProps) {

  override def complete(fn: () => Unit): Unit = {
    testPromise.success(true)
    super.complete(fn)
  }
}

class GetAllRevisionFdrTest(testPromise: Promise[Boolean], override val repositories: Repositories, override val actorProps: ActorProps)
    extends GetAllRevisionFdrActorPerRequest(repositories, actorProps) {

  override def complete(fn: () => Unit): Unit = {
    testPromise.success(true)
    super.complete(fn)
  }
}

class NodoInviaFlussoRendicontazioneTest(override val repositories: Repositories, override val actorProps: ActorProps) extends NodoInviaFlussoRendicontazioneActorPerRequest(repositories, actorProps) {
  override def receive: Receive = {
    case "init" =>
      replyTo = sender()
      req = SoapRequest("", "", "", "", "", Util.now(), ReExtra(), None)
    case "error" =>
      actorError(exception.DigitPaException("TEST ERROR", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
  }
}

class NodoChiediElencoFlussiRendicontazioneTest(override val repositories: Repositories, override val actorProps: ActorProps) extends NodoChiediElencoFlussiRendicontazioneActorPerRequest(repositories, actorProps) {
  override def receive: Receive = {
    case "init" =>
      replyTo = sender()
      req = SoapRequest("", "", "", "", "", Util.now(), ReExtra(), None)
    case "error" =>
      actorError(exception.DigitPaException("TEST ERROR", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
  }
}

class NodoChiediFlussoRendicontazioneTest(override val repositories: Repositories, override val actorProps: ActorProps) extends NodoChiediFlussoRendicontazioneActorPerRequest(repositories, actorProps) {
  override def receive: Receive = {
    case "init" =>
      replyTo = sender()
      req = SoapRequest("", "", "", "", "", Util.now(), ReExtra(), None)
    case "error" =>
      actorError(exception.DigitPaException("TEST ERROR", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
  }
}

//@org.scalatest.Ignore
class ActorErrorUnitTests() extends BaseUnitTest {
  //SOAP-------------------------------------------------------------
  "NodoInviaFlussoRendicontazioneTest" must {
    "at message" in {
      val act = system.actorOf(Props(classOf[NodoInviaFlussoRendicontazioneTest], repositories, props), s"NodoInviaFlussoRendicontazioneTest${Util.now()}")
      act ! "init"
      act ! "error"
      val res = expectMsgType[SoapResponse](singletesttimeout)
      assert(res.payload.isDefined)
      assert(res.payload.get.contains("<esito>KO</esito>"))
      assert(res.payload.get.contains("PPT_SYSTEM_ERROR"))
    }
  }
  "NodoChiediElencoFlussiRendicontazioneTest" must {
    "at message" in {
      val act = system.actorOf(Props(classOf[NodoChiediElencoFlussiRendicontazioneTest], repositories, props), s"NodoChiediElencoFlussiRendicontazioneTest${Util.now()}")
      act ! "init"
      act ! "error"
      val res = expectMsgType[SoapResponse](singletesttimeout)
      assert(res.payload.isDefined)
      assert(res.payload.get.contains("<description>TEST ERROR</description>"))
      assert(res.payload.get.contains("PPT_SYSTEM_ERROR"))
    }
  }
  "NodoChiediFlussoRendicontazioneTest" must {
    "at message" in {
      val act = system.actorOf(Props(classOf[NodoChiediFlussoRendicontazioneTest], repositories, props), s"NodoChiediFlussoRendicontazioneTest${Util.now()}")
      act ! "init"
      act ! "error"
      val res = expectMsgType[SoapResponse](singletesttimeout)
      assert(res.payload.isDefined)
      assert(res.payload.get.contains("<description>TEST ERROR</description>"))
      assert(res.payload.get.contains("PPT_SYSTEM_ERROR"))
    }
  }

}
