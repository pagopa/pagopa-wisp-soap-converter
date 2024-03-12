package it.gov.pagopa.tests

import akka.actor.Props
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RequestContext
import it.gov.pagopa.actors.{ApiConfigActor, CheckCache}
import org.mockito.MockitoSugar.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.BeforeAndAfterAll

import java.util.UUID
import scala.concurrent.Future

//@org.scalatest.Ignore
class ConfigUnitTests() extends BaseUnitTest with BeforeAndAfterAll{

  def toAnswerWithArgs[T](f: InvocationOnMock => T) = new Answer[T] {
    override def answer(i: InvocationOnMock): T = f(i)
  }
  val requestContext = mock[RequestContext](toAnswerWithArgs((i)=>{
    val res = i.getArgument(0).asInstanceOf[ToResponseMarshallable].value.asInstanceOf[HttpResponse]
    Future.successful(akka.http.scaladsl.server.RouteResult.Complete.apply(res))
  })
  )

  "ApiConfigActor" must {
    "get cache" in {
      val sessionid = UUID.randomUUID().toString
      MockserverExtension(system).when(
        request()
          .withMethod("GET")
          .withPath("/id")
      )
      .respond(response().withBody(s"""{"version":"${sessionid}"}"""));
      MockserverExtension(system).when(
          request()
            .withMethod("GET")
            .withPath("/")
        )
        .respond(response().withBody(s"""{"version":"${sessionid}"}"""));

      val apiConfigActor =
        system.actorOf(
          Props.create(
            classOf[ApiConfigActor],
            cosmosRepository,
            props)
        )


      apiConfigActor ! CheckCache(sessionid)

      var attempts=0
      while(props.ddataMap.version!= sessionid && attempts<10){
        attempts=attempts+1
        Thread.sleep(1000)
      }
      assert(attempts<10)
    }
  }
}
