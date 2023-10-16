package eu.sia.pagopa.config.actor

import eu.sia.pagopa.Main.SSlContext
import eu.sia.pagopa.common.actor.BaseActor
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.enums.SchedulerFireExitStatus
import eu.sia.pagopa.common.util.{ConfigUtil, Constant, JobUtil}
import eu.sia.pagopa.{ActorProps, BootstrapUtil, TestDData}

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
case class GetCache(override val sessionId: String, cacheId: String) extends BaseMessage
case class CheckCache(override val sessionId: String) extends BaseMessage
final case class ApiConfigActor(repositories: Repositories, actorProps: ActorProps) extends BaseActor {

  final val THREAD_SLEEP = 2000

  implicit val system = context.system

  private val scheduleMinutes: FiniteDuration = context.system.settings.config.getInt("configScheduleMinutes").minute

  context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))

  override def receive: Receive = {
    case CheckCache(sid) =>
      log.debug("checking api-config cache")
      (for {
        lastCacheId <- ConfigUtil.getConfigVersion(actorProps.httpsConnectionContext)
        _ =
          if (lastCacheId.isDefined && lastCacheId.get != actorProps.ddataMap.version) {
            self ! GetCache(sid, lastCacheId.get)
          } else {
            log.debug("no new cache version found")
            context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
          }
      } yield ()).recover({ case e =>
        log.error(e, s"ConfigUtil.getConfigIdHttp error")
        context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
      })
    case GetCache(_, cacheId) =>
      log.info(s"GetCache $cacheId requested")
      val env = scala.util.Properties.envOrNone("INSTANCE")
      (for {
        cacheData <- ConfigUtil.getConfigHttp(actorProps.httpsConnectionContext)
        _ = if (env.isDefined && env.get == "LOCAL") {
          actorProps.ddataMap = TestDData.ddataMap
        } else {
          cacheData.map(cd => {
            log.info(s"Received new configData ${cd.version},setting in memory")
            actorProps.ddataMap = cd
          })
          log.debug(s"GetCache $cacheId done")
        }
      } yield ())
        .recover({ case e =>
          log.error(e, s"GetCache $cacheId error")
        })
        .map(_ => {
          context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
        })

    case req: WorkRequest =>
      log.debug(s"WorkRequest ${req.jobName}")
      val replyTo = sender()
      Thread.sleep(THREAD_SLEEP)
      val enabled = JobUtil.isJobEnabled(log, actorProps.ddataMap, req.jobName)
      val suspend = JobUtil.areJobsSuspended(log, actorProps.ddataMap)

      val pipeline = if (enabled && !suspend) {
        for {
          status <- repositories.fdrRepository.fireJobIfNotRunning(req.jobName, Constant.KeyName.EMPTY_KEY)
          _ <- status match {
            case SchedulerFireExitStatus.JOB_STARTED =>
              for {
                d <- ConfigUtil.refreshConfigHttp(actorProps,false)
                _ = actorProps.ddataMap = d
                _ <- repositories.fdrRepository
                  .stopRunningJob(req.jobName, Constant.KeyName.EMPTY_KEY)
                  .recover({ case e =>
                    log.error(e, "Errore allo stop del job")
                  })
              } yield ()
            case SchedulerFireExitStatus.JOB_RUNNING =>
              Future.successful(())
          }
        } yield ()
      } else {
        Future.successful(())
      }

      pipeline
        .recoverWith({ case cause: Throwable =>
          log.warn(cause, s"Errore durante ${BootstrapUtil.actorClassId(getClass)}, message: [${cause.getMessage}]")
          repositories.fdrRepository
            .stopRunningJob(req.jobName, Constant.KeyName.EMPTY_KEY)
            .recover({ case e =>
              log.error(e, "Errore allo stop del job")
            })
        })
        .map(_ => {
          replyTo ! WorkResponse(req.sessionId, req.testCaseId, None, None)
        })

  }

}
