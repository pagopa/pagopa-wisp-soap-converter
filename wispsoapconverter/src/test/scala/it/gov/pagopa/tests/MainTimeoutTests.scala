//package it.gov.pagopa.tests
//
//import org.mockserver.integration.ClientAndServer
//import org.mockserver.model.HttpRequest.request
//import org.mockserver.model.HttpResponse.response
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should
//import sttp.client4.httpclient.HttpClientSyncBackend
//import sttp.client4.{UriContext, basicRequest}
//
//import java.io.File
//import java.util.UUID
//import scala.concurrent.duration.Duration
//import scala.concurrent.{Await, Future}
//import scala.util.{Failure, Success, Try}
//
////@org.scalatest.Ignore
//
//class MainTimeoutTests  extends AnyFlatSpec with should.Matchers {
//  def await[T](f: Future[T]): T = {
//    Await.result(f, Duration.Inf)
//  }
//    "Main" should "SYSTEM_ERROR" in {
//      val sessionid = UUID.randomUUID().toString
//      val mockServer = ClientAndServer.startClientAndServer(55555)
//      mockServer.when(
//          request()
//            .withMethod("GET")
//            .withPath("/")
//        )
//        .respond(response().withBody(
//          s"""{
//             |"version":"${sessionid}",
//             |"configurations":{
//             |        "GLOBAL-validate_input": {
//             |            "category": "GLOBAL",
//             |            "key": "validate_input",
//             |            "value": "true",
//             |            "description": null
//             |        },
//             |        "GLOBAL-validate_output": {
//             |            "category": "GLOBAL",
//             |            "key": "validate_output",
//             |            "value": "true",
//             |            "description": null
//             |        },
//             |        "GLOBAL-idCanaleAGID": {
//             |            "category": "GLOBAL",
//             |            "key": "idCanaleAGID",
//             |            "value": "true",
//             |            "description": null
//             |        },
//             |        "GLOBAL-idPspAGID": {
//             |            "category": "GLOBAL",
//             |            "key": "idPspAGID",
//             |            "value": "true",
//             |            "description": null
//             |        },
//             |        "GLOBAL-intPspAGID": {
//             |            "category": "GLOBAL",
//             |            "key": "intPspAGID",
//             |            "value": "true",
//             |            "description": null
//             |        }
//             |}
//             |},
//             |""".stripMargin));
//
//      System.setProperty("config.app",s"${new File(".").getCanonicalPath}/wispsoapconverter/src/test/resources/config-app-timeout.conf")
//      MainTestMain.main(Array(
//        "AKKA_SYSTEM_NAME=wispsoapconv-dev",
//        "REQUIRED_CONTACT_POINT_NR=1",
//        "SERVICE_HTTP_BIND_HOST=localhost",
//        s"SERVICE_HTTP_BIND_PORT=${mockServer.getPort+1}",
//      ))
//      await(MainTestMain.getBootstrapFuture)
//      assert(true)
//      Thread.sleep(1000)
//      val clientrequest = basicRequest
//        // send the body as form data (x-www-form-urlencoded)
//        .body("<xml></xml>")
//        .headers(Map(
//          "SOAPAction"->"nodoInviaRPT"
//        ))
//        // use an optional parameter in the URI
//        .post(uri"http://localhost:${mockServer.getPort+1}/webservices/input")
//      val backend = HttpClientSyncBackend()
//      val clientresponse = clientrequest.send(backend)
//      assert(clientresponse.body.left.getOrElse("").contains("PPT_SYSTEM_ERROR"))
//      assert(true)
//    }
//
//}
