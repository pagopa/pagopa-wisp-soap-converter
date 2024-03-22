package it.gov.pagopa.tests

import akka.actor.Props
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import it.gov.pagopa.actors.NodoInviaRPTActorPerRequest
import it.gov.pagopa.common.actor.PrimitiveActor
import it.gov.pagopa.common.util.Util
import it.gov.pagopa.soapinput.actor.SoapActorPerRequest
import it.gov.pagopa.soapinput.message.SoapRouterRequest
import org.mockito.MockitoSugar.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

//@org.scalatest.Ignore
class SoapUnitTests() extends BaseUnitTest {

  def toAnswerWithArgs[T](f: InvocationOnMock => T) = new Answer[T] {
    override def answer(i: InvocationOnMock): T = f(i)
  }
  val requestContext = mock[RequestContext](toAnswerWithArgs((i)=>{
    val res = i.getArgument(0).asInstanceOf[ToResponseMarshallable].value.asInstanceOf[HttpResponse]
    Future.successful(akka.http.scaladsl.server.RouteResult.Complete.apply(res))
  })
  )

  "SoapActor" must {
    "not an xml" in {
      val promise = Promise.apply[akka.http.scaladsl.server.RouteResult]()
      val soapActor =
        system.actorOf(
          Props.create(
            classOf[SoapActorPerRequest],
            requestContext,
            promise,
            Map(),
            props)
        )
      val sessionid = UUID.randomUUID().toString
      val req = SoapRouterRequest(sessionid, """{"aa":"bb"}""",LocalDateTime.now(),None,None,None,None,None,None,Some("NodoInviaRPT"))
      soapActor ! req
      await(promise.future.mapTo[RouteResult.Complete].map(res => {
        log.info("completed")
        assert(res.getResponse.status.isFailure())
      }))
    }
    "nodoInviaRPT" in {
      val promise = Promise.apply[akka.http.scaladsl.server.RouteResult]()
      val routers = Map(
        "nodoInviaRPTRouter" -> system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequest], cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
      )
      val soapActor =
        system.actorOf(
          Props.create(
            classOf[SoapActorPerRequest],
            requestContext,
            promise,
            routers,
            props)
        )
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      val sessionid = UUID.randomUUID().toString
      val payload = nodoInviaRPTPayload(iuv,ccp,false,stazione = None)
      val req = SoapRouterRequest(sessionid, payload,LocalDateTime.now(),None,None,None,None,None,None,Some("nodoInviaRPT"))
      soapActor ! req
      await(
        for{
          res <- promise.future.mapTo[RouteResult.Complete]
          _ = assert(res.getResponse.status.isSuccess())
          resString <- res.getResponse.entity.toStrict(10.seconds).map(_.data.utf8String)
          _ = assert(resString.contains(sessionid))
        } yield true
      )
    }
    "nodoInviaRPT long iuv" in {
      val promise = Promise.apply[akka.http.scaladsl.server.RouteResult]()
      val routers = Map(
        "nodoInviaRPTRouter" -> system.actorOf(
          Props.create(classOf[PrimitiveActor], cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
      )
      val soapActor =
        system.actorOf(
          Props.create(
            classOf[SoapActorPerRequest],
            requestContext,
            promise,
            routers,
            props)
        )
      val iuv = s"${RandomStringUtils.randomNumeric(15)}IIIIITTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      val sessionid = UUID.randomUUID().toString
      val payload = nodoInviaRPTPayload(iuv,ccp,false,stazione = None)
      val req = SoapRouterRequest(sessionid, payload,LocalDateTime.now(),None,None,None,None,None,None,Some("nodoInviaRPT"))
      soapActor ! req
      await(
        for{
          res <- promise.future.mapTo[RouteResult.Complete]
          _ = assert(res.getResponse.status.isSuccess())
          resString <- res.getResponse.entity.toStrict(10.seconds).map(_.data.utf8String)
          _ = assert(resString.contains("PPT_SINTASSI_EXTRAXSD"))
        } yield true
      )
    }
  }
}
