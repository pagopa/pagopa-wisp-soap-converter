package it.gov.pagopa

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown, Props}
import akka.event.Logging
import akka.http.scaladsl.HttpExt
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import io.github.mweirauch.micrometer.jvm.extras.{ProcessMemoryMetrics, ProcessThreadMetrics}
import io.micrometer.core.instrument.binder.jvm.{ClassLoaderMetrics, JvmGcMetrics, JvmMemoryMetrics, JvmThreadMetrics}
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import it.gov.pagopa.actors.ApiConfigActor
import it.gov.pagopa.common.actor._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.common.util.azure.storage.StorageBuilder
import it.gov.pagopa.common.util.web.NodoRoute
import org.slf4j.MDC
import scalaz.BuildInfo

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}

object Main extends MainTrait{}
trait MainTrait {

  private[this] var _config: Config = _
  private[this] var _log: AppLogger = _
  private[this] var _ec: ExecutionContext = _
  private[this] var _system: ActorSystem = _
  private val bootstrapPromise = Promise.apply[ActorProps]()

  def getBootstrapFuture = bootstrapPromise.future
  def getActorSystem = _system

  def cosmosRepository = new CosmosRepository(_config, _log)(_ec)
  def storageBuilder: StorageBuilder = new StorageBuilder()

  def main(args: Array[String]): Unit = {
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val argsmap = args.map(s=>{
      val ss = s.split("=")
      ss.apply(0)->ss.apply(1)
    }).toMap

    val actorSystemName: String =
      sys.env.getOrElse("AKKA_SYSTEM_NAME",argsmap.getOrElse("AKKA_SYSTEM_NAME",throw new IllegalArgumentException("Actor system name must be defined by the actorSystemName property")))
    val httpHost =
      sys.env.getOrElse("SERVICE_HTTP_BIND_HOST",argsmap.getOrElse("SERVICE_HTTP_BIND_HOST",throw new IllegalArgumentException("HTTP bind host must be defined by the SERVICE_HTTP_BIND_HOST property")))
    val httpPort = sys.env.get("SERVICE_HTTP_BIND_PORT").map(_.toInt).getOrElse(argsmap.get("SERVICE_HTTP_BIND_PORT").map(_.toInt).getOrElse(throw new IllegalArgumentException("HTTP bind port must be defined by the SERVICE_HTTP_BIND_PORT property")))

    val file = new File(System.getProperty("config.app"))
    val referenceConfig = ConfigFactory.parseFile(file).getConfig("reference")
    val appConfig = ConfigFactory.parseFile(file).getConfig("app")

    val config: Config =
      ConfigFactory.defaultOverrides().withFallback(referenceConfig).withFallback(appConfig).resolve(ConfigResolveOptions.defaults)
        .withFallback(ConfigFactory.parseString("""akka.management.health-checks.readiness-checks {
                                                  |  cluster-membership = ""
                                                  |}""".stripMargin))
    _config = config
    implicit val system: ActorSystem = ActorSystem(actorSystemName, config)
    _system = system
    implicit val log: AppLogger = new AppLogger(Logging(system, getClass.getCanonicalName))
    _log=log


    val shutdown = CoordinatedShutdown(system)

    log.info(s"""using config:
                |configScheduleMinutes: ${config.getString("configScheduleMinutes")}
                |waitAsyncProcesses: ${config.getString("waitAsyncProcesses")}
                |coordinatedShutdownHttpTimeout: ${config.getString("coordinatedShutdownHttpTimeout")}
                |coordinatedShutdownTerminationTimeout: ${system.settings.config.getInt("coordinatedShutdownTerminationTimeout")}
                |bundleTimeoutSeconds: ${config.getString("bundleTimeoutSeconds")}""".stripMargin)

    def filler(string: String, spacer: Int = 29): String = {
      val l = (spacer - (string.length)) / 2
      val fill = s"${"#" * l}${s"$string"}${"#" * l}"
      s"$fill${"#" * (spacer - fill.length)}"
    }

    log.info(s"""
                |${filler(s" ${BuildInfo.version} ")}
                |# ██     ██ ███████ ███████ #
                |# ██     ██ ██      ██      #
                |# ██  █  ██ ███████ ██      #
                |# ██ ███ ██      ██ ██      #
                |#  ███ ███  ███████ ███████ #
                |#############################""".stripMargin)
    log.info(s"ActorSystem $actorSystemName created")

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    _ec = executionContext
    implicit val materializer: Materializer = Materializer(system)

    val waitAsyncProcesses: Boolean = system.settings.config.getBoolean("waitAsyncProcesses")
    val coordinatedShutdownHttpTimeout: Int = system.settings.config.getInt("coordinatedShutdownHttpTimeout")

    val http: HttpExt = akka.http.scaladsl.Http()(system)

    def micrometerServer() = {
      val micrometerHost = config.getString("micrometer.http-server.host")
      val micrometerPort = config.getInt("micrometer.http-server.port")
      import akka.http.scaladsl.model._
      import akka.http.scaladsl.server.Directives._

      val prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

      new ProcessMemoryMetrics().bindTo(prometheusRegistry)
      new ProcessThreadMetrics().bindTo(prometheusRegistry)

      new ClassLoaderMetrics().bindTo(prometheusRegistry);
      new JvmMemoryMetrics().bindTo(prometheusRegistry);
      new JvmGcMetrics().bindTo(prometheusRegistry);
      new ProcessorMetrics().bindTo(prometheusRegistry);
      new JvmThreadMetrics().bindTo(prometheusRegistry);

      val route =
        pathSingleSlash {
          get {
            val response = prometheusRegistry.scrape();
            complete(HttpEntity.apply(ContentTypes.`text/plain(UTF-8)`, response.getBytes))
          }
        }

      http
        .newServerAt(micrometerHost, micrometerPort)
        .bind(route)
        .map(f => {
          log.info(s"Service Micrometers UP http://$micrometerHost:$micrometerPort")
          shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, s"http-unbind") { () =>
            log.info("Unbinding micrometer http server")
            f.unbind()
          }
          shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"http-terminate") { () =>
            log.info(s"Terminating micrometer server in max ${coordinatedShutdownHttpTimeout} seconds")
            f.terminate(coordinatedShutdownHttpTimeout.seconds).map(_ => Done)
          }
          f
        })
    }

    log.info(s"Starting Micrometer...")
    micrometerServer()
    shutdown.addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, s"dbs-stop") { () =>
      Future.successful(Done)
    }

    def getRouteesUntilEmpty(act: ActorRef): Future[Done] = {
      import akka.pattern.ask
      act
        .ask(Done)(10.seconds)
        .mapTo[Boolean]
        .flatMap(rts =>
          if (rts) {
            log.info(s"${act.path.name} done,closing")
            Future.successful(Done)
          } else {
            log.info(s"${act.path.name} NOT done,continuing")
            Thread.sleep(2000)
            getRouteesUntilEmpty(act)
          }
        )
        .recover({ case e =>
          log.error(e, "error asking actor for routees")
          Done
        })
    }
    log.info("Loading ConfigData...")
    (for {
      ddata <- for {
        _ <- Future.successful(())
        cfgData <- ConfigUtil.getConfigHttp()
        data <- Future.successful(cfgData)
      } yield data
      _ = log.info(s"ConfigData ${ddata.version} loaded")
    } yield ddata)
      .map(data => {
        val baseActorsNamesAndTypes: Seq[(String, Class[_ <: BaseActor])] =
          Seq(BootstrapUtil.actorClassId(classOf[ApiConfigActor]) -> classOf[ApiConfigActor])

        val primitiveActorsNamesAndTypes: Seq[(String, Class[_ <: BaseActor])] = (Primitive.soap.keys).map(s => s -> classOf[PrimitiveActor]).toSeq

        log.info("Creating Routers...")
        val baserouters: Map[String, ActorRef] = BootstrapUtil.createLocalRouters(system, baseActorsNamesAndTypes)
        val primitiverouters: Map[String, ActorRef] = BootstrapUtil.createLocalRouters(system, primitiveActorsNamesAndTypes)

        log.info(s"Created Routers:\n${(baserouters.keys ++ primitiverouters.keys).grouped(5).map(_.mkString(",")).mkString("\n")}")

        val reEventFunc: ReEventFunc = storageBuilder.build()

        val actorProps = ActorProps(
          http,
          actorMaterializer = materializer,
          routers = baserouters ++ primitiverouters,
          reEventFunc = reEventFunc,
          actorClassId = "main",
          ddataMap = data
        )

        log.info("Creating Actors...")
        val baseactors = BootstrapUtil.createActors(system, cosmosRepository,
            actorProps, baseActorsNamesAndTypes)
          .+("deadLetterMonitorActor"-> system.actorOf(Props.create(classOf[DeadLetterMonitorActor]), BootstrapUtil.actorClassId(classOf[DeadLetterMonitorActor]))
          )
        val primitiveactors: Map[String, ActorRef] = BootstrapUtil.createActors(system, cosmosRepository,
          actorProps, primitiveActorsNamesAndTypes)

        log.info(s"Created Actors:\n${(baseactors.keys ++ primitiveactors.keys).grouped(5).map(_.mkString(",")).mkString("\n")}")

        if (waitAsyncProcesses) {
          shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"actors-stop") { () =>
            log.info("waiting running actors...")
            Future.sequence(primitiveactors.map(f => getRouteesUntilEmpty(f._2))).flatMap(_ => Future.successful(Done))
          }
        }

        log.info(s"Starting HTTP Service Soap...")
        val routes = NodoRoute(
          system = system,
          routers = baserouters ++ primitiverouters,
          httpHost = httpHost,
          httpPort = httpPort,
          actorProps
        )
        import akka.http.scaladsl.server.Directives._
        http
          .newServerAt(httpHost, httpPort)
          .bind(routes.route ~ routes.soapFunction(actorProps))
          .map(f => {
            log.info(s"Starting AkkaManagement...")
            val management: AkkaManagement = AkkaManagement(system)
            management.start()
            log.info(s"Service online http://$httpHost:$httpPort")

            shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, s"http-unbind") { () =>
              log.info("Unbinding http server")
              f.unbind()
            }
            shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"http-terminate") { () =>
              log.info(s"Terminating http server in max ${coordinatedShutdownHttpTimeout} seconds")
              f.terminate(coordinatedShutdownHttpTimeout.seconds).map(_ => Done)
            }
          })
        actorProps
      }).map(ap=>{
        bootstrapPromise.success(ap)
        ap
      })
      .recoverWith({ case e =>
        log.error(e, "Bootstrap error")
        bootstrapPromise.failure(e)
        system.terminate()
        Future.failed(e)
      })
  }
}
object BootstrapUtil {
  def createActors(system: ActorSystem,
                   cosmosRepository: CosmosRepository,
                   actorProps: ActorProps, actorNamesAndTypes: Seq[(String, Class[_ <: BaseActor])]): Map[String, ActorRef] = {
    actorNamesAndTypes
      .map(a => {
        val actorName = a._1
        actorName -> system.actorOf(Props.create(a._2,
          cosmosRepository,
          actorProps), actorName)
      })
      .toMap
  }

  def createLocalRouters[T](system: ActorSystem, actors: Seq[(String, Class[_ <: BaseActor])]): Map[String, ActorRef] = {
    actors
      .map(a => {
        val router = BootstrapUtil.actorRouter(a._1)
        router -> Util.createLocalRouter(a._1, router)(system)
      })
      .toMap
  }

  def camelLowerCase(str: String): String = s"${str.charAt(0).toLower}${str.substring(1)}"

  def actorClassId[T](clazz: Class[T]): String = camelLowerCase(clazz.getSimpleName)

  def actorRouter[T](clazz: Class[T]) = s"${actorClassId(clazz)}Router"

  def actorRouter(str: String) = s"${str}Router"
}
final case class ActorProps(
                             http: HttpExt,
                             actorMaterializer: Materializer,
                             routers: Map[String, ActorRef],
                             reEventFunc: ReEventFunc,
                             actorClassId: String,
                             var ddataMap: ConfigData
)
