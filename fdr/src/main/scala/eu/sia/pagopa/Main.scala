package eu.sia.pagopa

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown, Props}
import akka.event.Logging
import akka.http.scaladsl.{HttpExt, HttpsConnectionContext}
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor._
import eu.sia.pagopa.common.message.{TriggerJobRequest, TriggerJobResponse}
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.{ContainerBlobFunc, ReEventFunc}
import eu.sia.pagopa.common.util.azurehubevent.sdkazureclient.AzureProducerBuilder
import eu.sia.pagopa.common.util.azurestorageblob.AzureStorageBlobClient
import eu.sia.pagopa.common.util.web.NodoRoute
import eu.sia.pagopa.config.actor.ApiConfigActor
import eu.sia.pagopa.nodopoller.actor.PollerActor
import io.github.mweirauch.micrometer.jvm.extras.{ProcessMemoryMetrics, ProcessThreadMetrics}
import io.micrometer.core.instrument.binder.jvm.{ClassLoaderMetrics, JvmGcMetrics, JvmMemoryMetrics, JvmThreadMetrics}
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import it.pagopa.config.ConfigDataV1
import org.slf4j.MDC

import java.io.File
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

 //alias for config version

object Main extends App {

  type ConfigData = ConfigDataV1

  val job = args.headOption

  val actorSystemName: String =
    sys.env.getOrElse("AKKA_SYSTEM_NAME", throw new IllegalArgumentException("Actor system name must be defined by the actorSystemName property"))
  val httpHost =
    sys.env.getOrElse("SERVICE_HTTP_BIND_HOST", throw new IllegalArgumentException("HTTP bind host must be defined by the SERVICE_HTTP_BIND_HOST property"))
  val httpPort = sys.env.get("SERVICE_HTTP_BIND_PORT").map(_.toInt).getOrElse(throw new IllegalArgumentException("HTTP bind port must be defined by the SERVICE_HTTP_BIND_PORT property"))

  val file = new File(System.getProperty("config.app"))
  val referenceConfig = ConfigFactory.parseFile(file).getConfig("reference")
  val dbConfig = ConfigFactory.parseFile(file).getConfig("db")
  val appConfig = ConfigFactory.parseFile(file).getConfig("app")

  val config: Config = if (job.isDefined) {
    val specificJobConfig = Try(ConfigFactory.parseFile(file).getConfig(s"jobs.${job.get}")).getOrElse(ConfigFactory.empty())
    val appJobsConfig = Try(ConfigFactory.parseFile(file).getConfig("jobs.all")).getOrElse(ConfigFactory.empty())
    ConfigFactory
      .defaultOverrides()
      .withFallback(specificJobConfig)
      .withFallback(appJobsConfig)
      .withFallback(dbConfig)
      .withFallback(referenceConfig)
      .withFallback(appConfig)
      .resolve(ConfigResolveOptions.defaults)
  } else {
    val c1 =
      ConfigFactory.defaultOverrides().withFallback(dbConfig).withFallback(referenceConfig).withFallback(appConfig).resolve(ConfigResolveOptions.defaults)
    c1.withFallback(ConfigFactory.parseString("""akka.management.health-checks.readiness-checks {
                                                |  cluster-membership = ""
                                                |}""".stripMargin))
  }

  implicit val system: ActorSystem = ActorSystem(actorSystemName, config)
  implicit val log: NodoLogger = new NodoLogger(Logging(system, getClass.getCanonicalName))
  val shutdown = CoordinatedShutdown(system)

  log.info(s"""using config:
              |configScheduleMinutes: ${config.getString("configScheduleMinutes")}
              |limitjobsSize: ${config.getString("limitjobsSize")}
              |coordinatedShutdown: ${config.getString("coordinatedShutdown")}
              |waitAsyncProcesses: ${config.getString("waitAsyncProcesses")}
              |coordinatedShutdownHttpTimeout: ${config.getString("coordinatedShutdownHttpTimeout")}
              |coordinatedShutdownTerminationTimeout: ${system.settings.config.getInt("coordinatedShutdownTerminationTimeout")}
              |bundleTimeoutSeconds: ${config.getString("bundleTimeoutSeconds")}""".stripMargin)

  def filler(string: String, spacer: Int = 34): String = {
    val l = (spacer - (string.length)) / 2
    val fill = s"${"#" * l}${s"$string"}${"#" * l}"
    s"$fill${"#" * (spacer - fill.length)}"
  }

  log.info(s"""
              |${filler(s" ${Constant.APP_VERSION} ")}
              |#                                #
              |#    ███████╗██████╗ ██████╗     #
              |#    ██╔════╝██╔══██╗██╔══██╗    #
              |#    █████╗  ██║  ██║██████╔╝    #
              |#    ██╔══╝  ██║  ██║██╔══██╗    #
              |#    ██║     ██████╔╝██║  ██║    #
              |#    ╚═╝     ╚═════╝ ╚═╝  ╚═╝    #
              |#                                #
              |${filler(job.map(j => s" Job: $j ").getOrElse(""))}""".stripMargin)
  log.info(s"ActorSystem $actorSystemName created")

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = Materializer(system)

  val cacertsPath: String = system.settings.config.getString("app.bundle.cacerts.path")

  val coordinatedShutdown: Boolean = system.settings.config.getBoolean("coordinatedShutdown")
  val waitAsyncProcesses: Boolean = system.settings.config.getBoolean("waitAsyncProcesses")
  val coordinatedShutdownHttpTimeout: Int = system.settings.config.getInt("coordinatedShutdownHttpTimeout")
  val coordinatedShutdownTerminationTimeout: Int = system.settings.config.getInt("coordinatedShutdownTerminationTimeout")

  val http: HttpExt = akka.http.scaladsl.Http()(system)

  def micrometerServer() = {
    val micrometerHost = config.getString("micrometer.http-server.host")
    val micrometerPort = config.getInt("micrometer.http-server.port")
    val micrometerHostname = config.getString("micrometer.http-server.hostname")
    import akka.http.scaladsl.model._
    import akka.http.scaladsl.server.Directives._

    val prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    //    prometheusRegistry.config().commonTags("application", micrometerHostname)

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
        if (coordinatedShutdown) {
          shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, s"http-unbind") { () =>
            log.info("Unbinding http server")
            f.unbind()
          }
          shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"http-terminate") { () =>
            log.info(s"Terminating micrometer server in max ${coordinatedShutdownHttpTimeout} seconds")
            f.terminate(coordinatedShutdownHttpTimeout.seconds).map(_ => Done)
          }
        }
        f
      })
  }

  log.info(s"Starting Micrometer...")
  micrometerServer()

  val repositories = Repositories(config, log)

  if (coordinatedShutdown) {
    shutdown.addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, s"dbs-stop") { () =>
      log.info("Stopping db connections...")
      if (repositories.fdrRepositoryInitialized) {
        repositories.fdrRepository.db.close()
      }
      Future.successful(Done)
    }
  } else {
    system.registerOnTermination(() => {
      log.info("Stopping db connections...")
      if (repositories.fdrRepositoryInitialized) {
        repositories.fdrRepository.db.close()
      }
    })
  }

  val SSlContext: HttpsConnectionContext = {
    val akkaSSLConfig: AkkaSSLConfig = NodoAkkaSSLConfig()(system)
    http.createClientHttpsContext(akkaSSLConfig)
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
  val bootstrapFuture = (for {
    ddata <- if( Seq("LOCAL").contains(Constant.INSTANCE) ){
      Future.successful(TestDData.ddataMap)
    } else {
      for {
        cfgData <- ConfigUtil.getConfigHttp(SSlContext)
        data <- cfgData match {
          case Some(c) => Future.successful(c)
          case None => Future.failed(new RuntimeException("Could not get ConfigData"))
        }
      } yield  data
    }
    _ = log.info("ConfigData loaded")
    _ = log.info("Check db connections")
    _ <- repositories.fdrRepository.testQuery()
  } yield ddata)
    .map(data => {
      val baseActorsNamesAndTypes: Seq[(String, Class[_ <: BaseActor])] = job match {
        case None =>
          Seq(
            BootstrapUtil.actorClassId(classOf[ApiConfigActor]) -> classOf[ApiConfigActor],
            BootstrapUtil.actorClassId(classOf[PollerActor]) -> classOf[PollerActor],
            Constant.KeyName.FTP_SENDER -> classOf[PrimitiveActor]
          )
        case Some(j) =>
          Seq(
            BootstrapUtil.actorClassId(classOf[PollerActor]) -> classOf[PollerActor],
            Constant.KeyName.FTP_SENDER -> classOf[PrimitiveActor]
          )
      }

      val primitiveActorsNamesAndTypes: Seq[(String, Class[_ <: BaseActor])] = job match {
        case None =>
          (Primitive.soap.keys ++ Primitive.jobs.keys).map(s => s -> classOf[PrimitiveActor]).toSeq
        case Some(j) =>
          Seq(j -> classOf[PrimitiveActor])
      }

      log.info("Creating Routers...")
      val baserouters: Map[String, ActorRef] = BootstrapUtil.createLocalRouters(system, baseActorsNamesAndTypes)
      val primitiverouters: Map[String, ActorRef] = BootstrapUtil.createLocalRouters(system, primitiveActorsNamesAndTypes)

      log.info(s"Created Routers:\n${(baserouters.keys ++ primitiverouters.keys).grouped(5).map(_.mkString(",")).mkString("\n")}")

      log.info(s"Starting Azure Hub Event Service ...")
      val reEventFunc: ReEventFunc = AzureProducerBuilder.build()

      log.info(s"Starting Azure Storage Blob Client Service ...")
      val containerBlobFunction: ContainerBlobFunc = AzureStorageBlobClient.build()

      val actorProps = ActorProps(
        http,
        SSlContext,
        actorMaterializer = materializer,
        actorUtility = new ActorUtility,
        routers = baserouters ++ primitiverouters,
        reEventFunc = reEventFunc,
        containerBlobFunction = containerBlobFunction,
        actorClassId = "main",
        cacertsPath = cacertsPath,
        ddataMap = data
      )

      log.info("Creating Actors...")
      val baseactors = BootstrapUtil.createActors(system, repositories, actorProps, baseActorsNamesAndTypes)
        .+("deadLetterMonitorActor"-> system.actorOf(Props.create(classOf[DeadLetterMonitorActor]), BootstrapUtil.actorClassId(classOf[DeadLetterMonitorActor]))
      )
      val primitiveactors: Map[String, ActorRef] = BootstrapUtil.createActors(system, repositories, actorProps, primitiveActorsNamesAndTypes)

      log.info(s"Created Actors:\n${(baseactors.keys ++ primitiveactors.keys).grouped(5).map(_.mkString(",")).mkString("\n")}")

      if (coordinatedShutdown && job.isEmpty && waitAsyncProcesses) {
        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"actors-stop") { () =>
          log.info("waiting running actors...")
          Future.sequence(primitiveactors.map(f => getRouteesUntilEmpty(f._2))).flatMap(_ => Future.successful(Done))
        }
      }

      job match {
        case None =>
          log.info(s"Starting HTTP Service (Seed,Soap,Rest)...")
          val routes = NodoRoute(
            system = system,
            fdrRepository = repositories.fdrRepository,
            routers = baserouters ++ primitiverouters,
            httpHost = httpHost,
            httpPort = httpPort,
            reEventFunc = reEventFunc,
            actorProps
          )
          import akka.http.scaladsl.server.Directives._
          http
            .newServerAt(httpHost, httpPort)
            .bind(routes.route ~ routes.routeSeed ~ routes.soapFunction(actorProps))
            .map(f => {
              if (job.isEmpty) {
                log.info(s"Starting AkkaManagement...")
                val management: AkkaManagement = AkkaManagement(system)
                management.start()
              }
              log.info(s"Service online http://$httpHost:$httpPort")

              if (coordinatedShutdown) {
                shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, s"http-unbind") { () =>
                  log.info("Unbinding http server")
                  f.unbind()
                }

                shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"http-terminate") { () =>
                  log.info(s"Terminating http server in max ${coordinatedShutdownHttpTimeout} seconds")
                  f.terminate(coordinatedShutdownHttpTimeout.seconds).map(_ => Done)
                }
              }

            })
        case Some(jobName) =>
          log.info(s"Triggering job [$jobName]")
          val sessionId = UUID.randomUUID().toString
          MDC.put(Constant.MDCKey.SESSION_ID, sessionId)
          import akka.pattern.ask

          import scala.concurrent.duration._
          implicit val timeout: Timeout = (system.settings.config.getInt("bundleTimeoutSeconds") + 10).seconds
          baserouters(BootstrapUtil.actorRouter(classOf[PollerActor]))
            .ask(TriggerJobRequest(sessionId, jobName, None, None))
            .mapTo[TriggerJobResponse]
            .flatMap(res => {
              log.info(s"JobResponse received [$jobName],waiting $coordinatedShutdownTerminationTimeout seconds to shutdown")
              Thread.sleep(coordinatedShutdownTerminationTimeout * 1000)
              system.terminate()
            })
            .recoverWith({ case e =>
              log.error(e, s"error in job execution,waiting $coordinatedShutdownTerminationTimeout seconds to shutdown")
              Thread.sleep(coordinatedShutdownTerminationTimeout * 1000)
              system.terminate()
            })

      }

      actorProps

    })
    .recoverWith({ case e =>
      log.error(e, "Bootstrap error")
      system.terminate()
      Future.failed(e)
    })

  def getBootstrapFuture = bootstrapFuture

  def getSystem: ActorSystem = system
}

object BootstrapUtil {
  def createActors(system: ActorSystem, repositories: Repositories, actorProps: ActorProps, actorNamesAndTypes: Seq[(String, Class[_ <: BaseActor])]): Map[String, ActorRef] = {
    actorNamesAndTypes
      .map(a => {
        val actorName = a._1
        actorName -> system.actorOf(Props.create(a._2, repositories, actorProps), actorName)
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
                             httpsConnectionContext: HttpsConnectionContext,
                             actorMaterializer: Materializer,
                             actorUtility: ActorUtility,
                             routers: Map[String, ActorRef],
                             reEventFunc: ReEventFunc,
                             containerBlobFunction: ContainerBlobFunc,
                             actorClassId: String,
                             cacertsPath: String,
                             var ddataMap: ConfigData
)
