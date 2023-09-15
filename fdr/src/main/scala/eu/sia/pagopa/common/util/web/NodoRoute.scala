package eu.sia.pagopa.common.util.web

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.javadsl.server.PathMatcher2
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, PathMatchers, Route, RouteResult}
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.Materializer
import akka.util.ByteString
import eu.sia.pagopa.common.exception.DigitPaErrorCodes
import eu.sia.pagopa.common.json.model.ActionResponse
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.fdr.FdrRepository
import eu.sia.pagopa.common.repo.fdr.enums.SchedulerFireCheckStatus
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc
import eu.sia.pagopa.nodopoller.actor.PollerActor
import eu.sia.pagopa.restinput.actor.RestActorPerRequest
import eu.sia.pagopa.restinput.message.RestRouterRequest
import eu.sia.pagopa.soapinput.actor.SoapActorPerRequest
import eu.sia.pagopa.soapinput.message.SoapRouterRequest
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC
import spray.json._

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class NodoRoute(
                      system: ActorSystem,
                      fdrRepository: FdrRepository,
                      routers: Map[String, ActorRef],
                      httpHost: String,
                      httpPort: Int,
                      reEventFunc: ReEventFunc,
                      actorProps: ActorProps
)(implicit val ec: ExecutionContext, log: NodoLogger, materializer: Materializer)
    extends CORSHandler {

  implicit val actorSys: ActorSystem =  system;

  val X_PDD_HEADER = system.settings.config.getString("pdd-host-header-name")

  def createSystemActorPerRequestAndTell[T <: BaseMessage](request: T, actorClassId: String, props: Props)(implicit log: NodoLogger, system: ActorSystem): Unit = {
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

  val akkaManagementRoute: Route = RouteDirectives.reject

  private def health: Route = {
    get {
      complete {
        val sessionId = UUID.randomUUID().toString
        createHttpResponse(ContentTypes.`text/plain(UTF-8)`, StatusCodes.OK, Constant.OK, sessionId)
      }
    }
  }

  def errorMessageJson(mex: String, ex: Option[Throwable] = None): String = {
    val stack = ex.map(e => s""","stack_trace" : "${e.getStackTrace.mkString("\\n\\t")}"""").getOrElse("")
    s"""{"error" : "$mex"$stack}""".stripMargin
  }

  private def resetRunningJob(): Route = {
    complete {
      import spray.json._
      fdrRepository
        .resetRunningJobs()
        .map(s => {
          HttpEntity(ContentTypes.`application/json`, ActionResponse(true, "Reset running jobs", s"Updated $s records").toJson.toString())
        })
        .recover({
          case e: Throwable => {
            HttpEntity(ContentTypes.`application/json`, ActionResponse(false, "Reset running jobs", e.getMessage).toJson.toString())
          }
        })
    }
  }

  private def version: Route = {
    complete {
      HttpEntity(
        ContentTypes.`application/json`,
        s"""{
           |"version" : "${Constant.APP_VERSION}",
           |"name" : "${Constant.APP_NAME}",
           |"instance" : "${Constant.INSTANCE}",
           |}""".stripMargin
      )
    }
  }

  val methods: Map[String, String] = Map(
    "notifyFlussoRendicontazione" -> "POST"
  )

  val methodsInternal: Map[String, String] = Map(
    "getAllRevisionFdr" -> "GET"
  )

  private def jobsStatus(): Route = {
    parameterMap { params =>
      complete {
        val timeFilter = params
          .get("timeFilter")
          .flatMap({
            case "1hour" =>
              Some(Util.now().minusHours(1))
            case "1day" =>
              Some(Util.now().minusDays(1))
            case "1week" =>
              Some(Util.now().minusWeeks(1))
            case _ =>
              None
          })
        val keyFilter = params.get("keyFilter")
        import spray.json._
        import DefaultJsonProtocol._
        (for {
          jobs <- fdrRepository.findJobs(timeFilter, keyFilter)
          groups = jobs
            .groupBy(_.jobName)
            .map(group => {
              JobGroup(
                Jobs.descr(group._1),
                group._1,
                group._2.map(dd => JobDto(dd.id, dd.extraKey, dd.start, dd.end, dd.status.toString)).sortBy(_.status),
                group._2.count(_.status == SchedulerFireCheckStatus.RUNNING)
              )
            })
            .toSeq
            .sortBy(_.jobName)
          res = s"""{
                   | "date" : "${Util.now()}",
                   | "jobs" : ${groups.toJson}
                   |}""".stripMargin
        } yield HttpEntity(ContentTypes.`application/json`, res)).recover({ case e: Throwable =>
          HttpEntity(ContentTypes.`application/json`, s"error ${e.getMessage}")
        })
      }
    }
  }

  private def keys(): Route = {
    complete {
      val sessionId = UUID.randomUUID().toString
      MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
      log.debug(s"START request Http keys[$sessionId]")

      val keys = Seq(
        "PA" -> "PA e stazioni",
        "PSP" -> "PSP e canali",
        "FTP_SERVER" -> "FTP SERVER",
        "CONFIG" -> "CONFIG",
        "PDD" -> "PDD",
        "INFORMATIVA_PA" -> "INFORMATIVA PA",
        "INFORMATIVA_CDI" -> "INFORMATIVA CDI",
        "CODICE_LINGUA" -> "CODICE LINGUA",
        "CATALOGO_SERVIZI" -> "CATALOGO_SERVIZI"
      )

      val payload = keys
        .map(r => {
          s"""{
             |"refreshableKey" : "${r._1}",
             |"refreshableKeyDescription" : "${r._2}",
             |"tableKeys" : ["${r._1} related tables"]}
             |""".stripMargin
        })
        .mkString("[", ",", "]")

      HttpEntity(ContentTypes.`application/json`, payload)
    }
  }

  private def config(): Route = {
    complete {
      val sessionId = UUID.randomUUID().toString
      MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
      log.debug(s"START request Http config[$sessionId]")

      val payload = actorProps.ddataMap.configurations.values
        .map(cfg => {
          s"""{
                 |"category":"${cfg.category}","key":"${cfg.key}",
                 |"value":"${cfg.value}"${cfg.description.map(d => s""","description":${JsString(d).toString()}""").getOrElse("")}
                 |}""".stripMargin
        })
        .mkString("[", ",", "]")

      HttpEntity(ContentTypes.`application/json`, payload)
    }
  }

  private def makeClogJson(clogs: Vector[(String, String, String, String)]) = {
    clogs
      .map(clog => {
        s"""{
           |"id":"${clog._1}",
           |"file":"${clog._2}",
           |"date":"${clog._3}",
           |"hash":"${clog._4}"
           |}""".stripMargin
      })
      .mkString("[", ",", "]")
  }

  private def changelogs(): Route = {
    complete {
      val sessionId = UUID.randomUUID().toString
      MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
      log.debug(s"START request Http config[$sessionId]")

      import fdrRepository.driver.api._
      val action = sql"select ID,FILENAME,DATEEXECUTED,MD5SUM from DATABASECHANGELOG order by ORDEREXECUTED asc".as[(String, String, String, String)]

      fdrRepository.runAction(action)
        .map(clogs => {
          val alllogs = makeClogJson(clogs)
          val p = s"""{
                   |"fdr": $alllogs,
                   |}""".stripMargin
          HttpEntity(ContentTypes.`application/json`, p)
        })

    }
  }

  private def checkRunningJobs() = {
    complete {
      val sessionId = UUID.randomUUID().toString
      MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
      log.debug(s"START request Http checkRunningJobs[$sessionId]")

      val runningJobs = fdrRepository
        .checkRunningJobs()
        .map(tot => HttpResponse(status = StatusCodes.OK.intValue, entity = HttpEntity(ContentTypes.`application/json`, tot.toString), headers = Nil))
        .recover({ case _: Throwable =>
          HttpResponse(status = StatusCodes.InternalServerError.intValue, entity = HttpEntity(ContentTypes.`application/json`, "ERROR"), headers = Nil)
        })
      runningJobs
    }
  }

  private def jobs(): Route = {
    complete {
      val sessionId = UUID.randomUUID().toString
      MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
      log.debug(s"START request Http jobs[$sessionId]")

      val resp = Jobs.toSeq
        .map(j => s"""{
                     |"name" : "${j._1}",
                     |"descr" : "${j._2}"
                     |}""".stripMargin)
        .mkString("[", ",", "]")

      HttpEntity(ContentTypes.`application/json`, resp)
    }
  }

  private def jobReset(): Route = {
    parameterMap { params =>
      complete {
        import spray.json._
        fdrRepository
          .resetJob(params("id").toLong)
          .map(_ => {
            HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, ActionResponse(true, "Reset job", s"Job resetted").toJson.toString()))
          })
          .recover({ case e =>
            HttpResponse(status = StatusCodes.InternalServerError, entity = HttpEntity(ContentTypes.`application/json`, ActionResponse(false, "Reset job", s"error ${e.getMessage}").toJson.toString()))
          })
      }
    }
  }

  private def jobTrigger(pollerActorRouter: ActorRef): Route = {
    corsHandler {
      withoutRequestTimeout {
        path(Segment) { segment =>
          get {
            parameterMap { parameters =>
              optionalHeaderValueByName("testCaseId") { headerTestCaseId =>
                complete {
                  import spray.json._
                  val sessionId = UUID.randomUUID().toString
                  MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
                  log.debug(s"START request Http jobTrigger [$sessionId] $segment")
                  pollerActorRouter
                    .ask(TriggerJobRequest(sessionId, segment, parameters.get("cron"), headerTestCaseId))(BUNDLE_IDLE_TIMEOUT)
                    .mapTo[TriggerJobResponse]
                    .map(resp => {
                      resp.schedulerStatus match {
                        case SchedulerStatus.OK =>
                          createHttpResponse(ContentTypes.`text/plain(UTF-8)`, StatusCodes.OK, ActionResponse(true, s"Trigger job ${segment}", resp.schedulerMessage.get).toJson.toString(), sessionId)
                        case SchedulerStatus.KO =>
                          createHttpResponse(
                            ContentTypes.`text/plain(UTF-8)`,
                            StatusCodes.InternalServerError,
                            ActionResponse(false, s"Trigger job ${segment}", resp.schedulerMessage.get).toJson.toString(),
                            sessionId
                          )
                      }
                    })
                    .recover({
                      case askTimeoutException: AskTimeoutException =>
                        log.error(askTimeoutException, s"AskTimeoutException [${BUNDLE_IDLE_TIMEOUT.toString}] ${askTimeoutException.getClass.getCanonicalName}")
                        createHttpResponse(
                          ContentTypes.`application/json`,
                          StatusCodes.InternalServerError,
                          ActionResponse(false, s"Trigger job ${segment}", s"Trigger failed,can't contact nodo-poller").toJson.toString(),
                          sessionId
                        )
                      case e: Throwable =>
                        log.error(e, s"Throwable ${e.getClass.getCanonicalName}")
                        createHttpResponse(
                          ContentTypes.`application/json`,
                          StatusCodes.InternalServerError,
                          ActionResponse(false, s"Trigger job ${segment}", s"Throwable ${e.getClass.getCanonicalName}").toJson.toString(),
                          sessionId
                        )
                    })
                }
              }
            }
          }
        }
      }
    }
  }

  val routeSeed: Route = {
    corsHandler {
      pathPrefix("monitor") {
        encodeResponseWith(Coders.Gzip) {
          path("version") { version } ~
          path("health") { health } ~
          path("keys") { keys() } ~
          path("config") { config() } ~
          path("changelogs") { changelogs() } ~
          path("jobs") { jobs() } ~
          path("jobs" / "checkRunningJobs") { checkRunningJobs() } ~
          path("jobs" / "reset") { jobReset() } ~
          path("jobsStatus") { jobsStatus() } ~
          path("resetRunningJob") { resetRunningJob() } ~
          get {
            pathEndOrSingleSlash {
              getFromResource(s"web/index.html")
            } ~
            getFromResourceDirectory("web")
          }
        } ~
        akkaManagementRoute
      }
    }
  }

  val route: Route = pathEndOrSingleSlash {
    complete(s"Server up and running ${Constant.KeyName.REST_INPUT}")
  } ~
    pathPrefix("jobs" / "trigger") { jobTrigger(routers(BootstrapUtil.actorRouter(classOf[PollerActor]))) } ~
    pathPrefix("config" / "refresh") { configRefresh() } ~
    path("web" / "refreshable-key") { refreshableKey() }

  private def configRefresh(): Route = {
    import scala.concurrent.duration._
    corsHandler {
      path(Segment) { segment =>
        withRequestTimeout(120.seconds) {
          optionalHeaderValueByName("testCaseId") { _ =>
            complete {
              val sessionId = UUID.randomUUID().toString
              MDC.put(Constant.MDCKey.SESSION_ID, sessionId)

              log.debug(s"START request Http configRefresh [$sessionId] ${segment}")

              (for{
                d <- ConfigUtil.refreshConfigHttp(actorProps,true)
                _ = actorProps.ddataMap = d
              } yield createHttpResponse(ContentTypes.`text/plain(UTF-8)`, StatusCodes.OK, "SUCCESS", sessionId)).recover({
                case e=>
                  e.printStackTrace()
                  createHttpResponse(ContentTypes.`text/plain(UTF-8)`, StatusCodes.InternalServerError, s"ERROR:${e.getMessage}", sessionId)
              })

            }
          }
        }
      }
    }
  }

  private def refreshableKey(): Route = {
    corsHandler {
      extractRequest { _ =>
        extractMethod { _ =>
          entity(as[String]) { _: String =>
            parameterMap { _ =>
              optionalHeaderValueByName("testCaseId") { _ =>
                complete {
                  val sessionId = UUID.randomUUID().toString
                  MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
                  log.debug(s"START request Http refreshableKey [$sessionId]")

                  val keys = Seq(
                    "PA" -> "PA e stazioni",
                    "PSP" -> "PSP e canali",
                    "FTP_SERVER" -> "FTP SERVER",
                    "CONFIG" -> "CONFIG",
                    "PDD" -> "PDD",
                    "INFORMATIVA_PA" -> "INFORMATIVA PA",
                    "INFORMATIVA_CDI" -> "INFORMATIVA CDI",
                    "CODICE_LINGUA" -> "CODICE LINGUA",
                    "CATALOGO_SERVIZI" -> "CATALOGO_SERVIZI"
                  )

                  val payload = keys
                    .map(r => {
                      s"""{
                         |"refreshableKey" : "${r._1}",
                         |"refreshableKeyDescription" : "${r._2}",
                         |"tableKeys" : ["${r._1} related tables"]}
                         |""".stripMargin
                    })
                    .mkString("[", ",", "]")

                  createHttpResponse(MediaTypes.`application/json`, StatusCodes.OK, payload, sessionId)
                }
              }
            }
          }
        }
      }
    }
  }

  private def createHttpResponse(contentType: ContentType.NonBinary, statusCode: StatusCode, payload: String, sessionId: String): HttpResponse = {
    log.debug(s"END request Http [$sessionId]")
    val SESSION_ID_HEADER = true
    HttpResponse(
      status = statusCode,
      entity = HttpEntity(contentType, payload),
      headers = if (SESSION_ID_HEADER) {
        RawHeader(Constant.HTTP_RESP_SESSION_ID_HEADER, sessionId) :: Nil
      } else {
        Nil
      }
    )
  }

  def akkaHttpTimeout(sessionId: String): HttpResponse = {
    val dpe = DigitPaErrorCodes.PPT_SYSTEM_ERROR
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some("Internal timeout"))
    MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
    Util.logPayload(log, Some(payload))
    log.debug(s"END request Http for AKKA HTTP TIMEOUT")
    log.info(FdrLogConstant.logEnd(Constant.KeyName.SOAP_INPUT))
    HttpResponse(status = StatusCodes.ServiceUnavailable, entity = HttpEntity(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`, payload))
  }

  def akkaErrorEncoding(sessionId: String, charset: String): HttpResponse = {
    val dpe = DigitPaErrorCodes.PPT_SYSTEM_ERROR
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(s"Error, data encoding is not $charset"))
    MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
    Util.logPayload(log, Some(payload))
    log.debug(s"END request Http for AKKA HTTP TIMEOUT")
    log.info(FdrLogConstant.logEnd(Constant.KeyName.SOAP_INPUT))
    HttpResponse(status = StatusCodes.BadRequest, entity = HttpEntity(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`, payload))
  }

  def soapFunction(actorProps: ActorProps): Route = {
    ignoreTrailingSlash {
      path("webservices" / "input") {
        val sessionId = UUID.randomUUID().toString
        MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
        log.info(FdrLogConstant.logStart(Constant.KeyName.SOAP_INPUT))
        import scala.concurrent.duration._
        val httpSeverRequestTimeout = FiniteDuration(httpSeverRequestTimeoutParam, SECONDS)
        withRequestTimeout(httpSeverRequestTimeout, _ => akkaHttpTimeout(sessionId)) {
          post {
            extractRequest { req =>
              extractClientIP { remoteAddress =>
                optionalHeaderValueByName(X_PDD_HEADER) { originalRequestAddresOpt =>
                  log.debug(s"Request headers:\n${req.headers.map(s => s"${s.name()} => ${s.value()}").mkString("\n")}")
                  optionalHeaderValueByName("SOAPAction") { soapActionHeader =>
                    log.info(s"Ricevuta request [${soapActionHeader.getOrElse("No SOAPAction")}] @ ${LocalDateTime.now()}")
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
                                originalRequestAddresOpt.flatMap(_.split(",").headOption).orElse(remoteAddress.toIP.map(_.ip.getHostAddress)),
                                soapActionHeader
                              )

                              val promise: Promise[RouteResult] = Promise[RouteResult]()

                              createSystemActorPerRequestAndTell[SoapRouterRequest](
                                soapRouterRequest,
                                Constant.KeyName.SOAP_INPUT,
                                Props(classOf[SoapActorPerRequest], ctx, promise, routers, reEventFunc, actorProps)
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

  def restFunction(actorProps: ActorProps): Route = {
    Primitive.rest
      .map(primi => {
        val primitiva = primi._1
        val httppath = primi._2._1
        val pathMatcher = PathMatchers.separateOnSlashes(httppath)
        val httpmethod = methods(primitiva)

        path(pathMatcher) {
          restRoute(primitiva, httpmethod, Map())
        }

      })
      .fold(RouteDirectives.reject)(_ ~ _)
  }

  def restFunctionInternal(actorProps: ActorProps): Route = {
    Primitive.restInternal
      .map(primi => {
        val primitiva = primi._1
        val pathMatcherVariables: PathMatcher[(String, String)] = "internal" / "organizations" / Segment / "fdrs" / Segment
        val httpmethod = methodsInternal(primitiva)

        path(pathMatcherVariables) {
          (organizationId, fdr) =>
              restRoute(primitiva, httpmethod, Map("organizationId" -> organizationId, "fdr" -> fdr))
        }

      })
      .fold(RouteDirectives.reject)(_ ~ _)
  }

  private def restRoute(primitiva: String, httpmethod: String, pathParams: Map[String, String]) = {
    val sessionId = UUID.randomUUID().toString
    MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
    log.info(s"Ricevuta request [$sessionId] @ ${LocalDateTime.now()} : [$primitiva]")
    MDC.put(Constant.MDCKey.ACTOR_CLASS_ID, primitiva)
    val httpSeverRequestTimeout = FiniteDuration(httpSeverRequestTimeoutParam, SECONDS)
    withRequestTimeout(httpSeverRequestTimeout, _ => akkaHttpTimeout(sessionId)) {
      method(HttpMethods.getForKey(httpmethod).get) {
        extractRequest { req =>
          extractClientIP { remoteAddress =>
            optionalHeaderValueByName(X_PDD_HEADER) { originalRequestAddresOpt =>
              log.debug(s"Request headers:\n${req.headers.map(s => s"${s.name()} => ${s.value()}").mkString("\n")}")
              extractRequestContext { ctx =>
                entity(as[ByteString]) { bs =>
                  parameterSeq { params =>
                    optionalHeaderValueByName("testCaseId") { headerTestCaseId =>
                      val cs = req.entity.contentType.charsetOption.getOrElse(HttpCharsets.`UTF-8`)
                      val payloadTry = Try(if (checkUTF8) {
                        val dec = cs.nioCharset().newDecoder()
                        dec.decode(bs.asByteBuffer).toString
                      } else {
                        bs.utf8String
                      })
                      payloadTry match {
                        case Success(payload) =>
                          log.info(FdrLogConstant.logStart(Constant.KeyName.REST_INPUT))
                          val request = ctx.request
                          log.info(s"Content-Type [${request.entity.contentType}]")

                          val restRouterRequest: RestRouterRequest = RestRouterRequest(
                            sessionId,
                            if (payload.isEmpty) None else Some(payload),
                            Util.now(),
                            headerTestCaseId,
                            Some(req.uri.toString()),
                            req.headers.map(h => (h.name(), h.value())),
                            params,
                            pathParams,
                            Some(httpmethod),
                            originalRequestAddresOpt.flatMap(_.split(",").headOption).orElse(remoteAddress.toIP.map(_.ip.getHostAddress)),
                            primitiva
                          )

                          val p: Promise[RouteResult] = Promise[RouteResult]()
                          createSystemActorPerRequestAndTell[RestRouterRequest](
                            restRouterRequest,
                            Constant.KeyName.REST_INPUT,
                            Props(classOf[RestActorPerRequest], ctx, p, routers, reEventFunc, actorProps)
                          )(log, system)
                          _ => p.future
                        case Failure(_) =>
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
