package it.gov.pagopa.common.util.web

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.util.ByteString
import it.gov.pagopa.ActorProps
import it.gov.pagopa.common.exception.DigitPaErrorCodes
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util._
import it.gov.pagopa.soapinput.actor.SoapActorPerRequest
import it.gov.pagopa.soapinput.message.SoapRouterRequest
import org.slf4j.MDC

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class NodoRoute(
                      system: ActorSystem,
                      routers: Map[String, ActorRef],
                      httpHost: String,
                      httpPort: Int,
                      actorProps: ActorProps
)(implicit val ec: ExecutionContext, log: AppLogger) {

  implicit val actorSys: ActorSystem =  system;

  def createSystemActorPerRequestAndTell[T <: BaseMessage](request: T, actorClassId: String, props: Props)(implicit log: AppLogger, system: ActorSystem): Unit = {
    val pr = s"pr-$actorClassId-${UUID.randomUUID().toString}-${request.sessionId}"
    log.debug(s"CREATE ActorPerRequest [$pr]")
    val perrequest = system.actorOf(props, pr)
    log.debug(s"TELL ActorPerRequest [$pr]")
    perrequest ! request
  }

  val httpSeverRequestTimeoutParam: Int = system.settings.config.getInt("config.http.server-request-timeout")
  val checkUTF8: Boolean = system.settings.config.getBoolean("bundle.checkUTF8")
  val BUNDLE_IDLE_TIMEOUT: FiniteDuration =
    FiniteDuration(system.settings.config.getInt("bundleTimeoutSeconds"), TimeUnit.SECONDS)

  val route: Route = pathEndOrSingleSlash {
    complete(s"Server up and running")
  }
  def infoRoute(actorProps: ActorProps): Route = {
    path("info"){
      get{
        complete {
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{
               |"version" : "${it.gov.pagopa.BuildInfo.version}",
               |"buildTime" : ${it.gov.pagopa.BuildInfo.buildTime},
               |"identifier" : "${Constant.SERVICE_IDENTIFIER}",
               |"cacheVersion": "${actorProps.ddataMap.version}"
               |}""".stripMargin
          )
        }
      }
    }
  }

  def akkaHttpTimeout(sessionId: String): HttpResponse = {
    val dpe = DigitPaErrorCodes.PPT_SYSTEM_ERROR
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some("Internal timeout"))
    MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
    Util.logPayload(log, Some(payload))
    log.debug(s"END request Http for AKKA HTTP TIMEOUT")
    log.info(LogConstant.logEnd(Constant.KeyName.SOAP_INPUT))
    HttpResponse(status = StatusCodes.ServiceUnavailable, entity = HttpEntity(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`, payload))
  }

  def akkaErrorEncoding(sessionId: String, charset: String): HttpResponse = {
    val dpe = DigitPaErrorCodes.PPT_SYSTEM_ERROR
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(s"Error, data encoding is not $charset"))
    MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
    Util.logPayload(log, Some(payload))
    log.debug(s"END request Http for AKKA HTTP TIMEOUT")
    log.info(LogConstant.logEnd(Constant.KeyName.SOAP_INPUT))
    HttpResponse(status = StatusCodes.BadRequest, entity = HttpEntity(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`, payload))
  }

  def soapFunction(actorProps: ActorProps): Route = {
    ignoreTrailingSlash {
      path("webservices" / "input") {
        val sessionId = UUID.randomUUID().toString
        MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
        log.info(LogConstant.logStart(Constant.KeyName.SOAP_INPUT))
        import scala.concurrent.duration._
        val httpSeverRequestTimeout = FiniteDuration(httpSeverRequestTimeoutParam, SECONDS)
        withRequestTimeout(httpSeverRequestTimeout, _ => akkaHttpTimeout(sessionId)) {
          post {
            extractRequest { req =>
              extractClientIP { remoteAddress =>
                  log.debug(s"Request headers:\n${req.headers.map(s => s"${s.name()} => ${s.value()}").mkString("\n")}")
                  optionalHeaderValueByName("SOAPAction") { soapActionHeader =>
                    log.info(s"Ricevuta request [${soapActionHeader.getOrElse("No SOAPAction")}] @ ${Instant.now()}")
                    optionalHeaderValueByName("testCaseId") { headerTestCaseId =>
                      extractRequestContext { ctx =>
                        entity(as[ByteString]) { bs =>
                          val cs = req.entity.contentType.charsetOption.getOrElse(HttpCharsets.`UTF-8`)
                          val payloadTry = Try (if (checkUTF8) {
                            val dec = cs.nioCharset().newDecoder()
                            dec.decode(bs.asByteBuffer).toString

                          } else {
                            bs.utf8String

                          })
                          payloadTry match {
                            case Success(payload) =>
                              val request = ctx.request
                              log.info(s"Content-Type [${request.entity.contentType}]")
                              val soapRouterRequest = SoapRouterRequest(
                                sessionId,
                                payload,
                                Util.now(),
                                headerTestCaseId,
                                Some(req.uri.toString()),
                                Some(req.headers.map(h => (h.name(), h.value()))),
                                None,
                                None,
                                remoteAddress.toIP.map(_.ip.getHostAddress),
                                soapActionHeader
                              )

                              val promise: Promise[RouteResult] = Promise[RouteResult]()

                              createSystemActorPerRequestAndTell[SoapRouterRequest](
                                soapRouterRequest,
                                Constant.KeyName.SOAP_INPUT,
                                Props(classOf[SoapActorPerRequest], ctx, promise, routers, actorProps)
                              )(log, system)
                              _ => promise.future
                            case Failure(e) =>
                              log.warn(e, "error reading request body")
                              complete(akkaErrorEncoding(sessionId, cs.value))
                          }
                        }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

}
