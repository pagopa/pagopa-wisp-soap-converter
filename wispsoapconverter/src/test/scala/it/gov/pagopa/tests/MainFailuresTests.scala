package it.gov.pagopa.tests

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.{UriContext, basicRequest}

import java.io.File
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

//@org.scalatest.Ignore

class MainFailuresTests  extends AnyFlatSpec with should.Matchers {
  def await[T](f: Future[T]): T = {
    Await.result(f, Duration.Inf)
  }

  "Main" should "fail for AKKA_SYSTEM_NAME" in {
    System.setProperty("config.app",s"${new File(".").getCanonicalPath}/wispsoapconverter/src/test/resources/config-app.conf")
    Try(MainTestMain.main(Array(
      "REQUIRED_CONTACT_POINT_NR=1",
      "SERVICE_HTTP_BIND_HOST=localhost",
      s"SERVICE_HTTP_BIND_PORT=44444",
    ))) match {
      case Failure(exception) => assert(exception.getMessage == "Actor system name must be defined by the actorSystemName property")
      case Success(value) => assert(false)
    }
  }
  "Main" should "fail for SERVICE_HTTP_BIND_HOST" in {
    System.setProperty("config.app",s"${new File(".").getCanonicalPath}/wispsoapconverter/src/test/resources/config-app.conf")
    Try(MainTestMain.main(Array(
      "AKKA_SYSTEM_NAME=wispsoapconv-dev",
      "REQUIRED_CONTACT_POINT_NR=1",
      s"SERVICE_HTTP_BIND_PORT=44444",
    ))) match {
      case Failure(exception) => assert(exception.getMessage == "HTTP bind host must be defined by the SERVICE_HTTP_BIND_HOST property")
      case Success(value) => assert(false)
    }
  }
  "Main" should "fail for SERVICE_HTTP_BIND_PORT " in {
    System.setProperty("config.app",s"${new File(".").getCanonicalPath}/wispsoapconverter/src/test/resources/config-app.conf")
    Try(MainTestMain.main(Array(
      "AKKA_SYSTEM_NAME=wispsoapconv-dev",
      "REQUIRED_CONTACT_POINT_NR=1",
      "SERVICE_HTTP_BIND_HOST=localhost"
    ))) match {
      case Failure(exception) => assert(exception.getMessage == "HTTP bind port must be defined by the SERVICE_HTTP_BIND_PORT property")
      case Success(value) => assert(false)
    }
  }
  "Main" should "fail for SERVICE_HTTP_BIND_PORT number" in {
    System.setProperty("config.app",s"${new File(".").getCanonicalPath}/wispsoapconverter/src/test/resources/config-app.conf")
    Try(MainTestMain.main(Array(
      "AKKA_SYSTEM_NAME=wispsoapconv-dev",
      "REQUIRED_CONTACT_POINT_NR=1",
      "SERVICE_HTTP_BIND_HOST=localhost",
      s"SERVICE_HTTP_BIND_PORT=host",
    ))) match {
      case Failure(exception) => assert(exception.getMessage == "For input string: \"host\"")
      case Success(value) => assert(false)
    }
  }

}
