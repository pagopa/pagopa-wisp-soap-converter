package eu.sia.pagopa.nodopoller.actor

import akka.pattern.ask
import akka.util.Timeout
import eu.sia.pagopa.common.actor.BaseActor
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

case class PollerActor(repositories: Repositories, actorProps: ActorProps) extends BaseActor {

  private val NOTHING_TO_DO_MSG = "Nothing to do"

  private implicit val timeout: Timeout = context.system.settings.config.getInt("bundleTimeoutSeconds").seconds

  private val limitJobsDays: Long = Try(context.system.settings.config.getLong("limitDays")).getOrElse(90)

  private def getRouter(job: String) = {
    actorProps.routers(BootstrapUtil.actorRouter(job))
  }

  def receive: Receive = { case tjr: TriggerJobRequest =>
    val replyTo = sender()

    val req = tjr.cron match {
      case Some(_) =>
        tjr.copy(sessionId = UUID.randomUUID().toString)
      case None =>
        tjr
    }

    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    MDC.put(Constant.MDCKey.ACTOR_CLASS_ID, actorClassId)


    val enabled = JobUtil.isJobEnabled(log, ddataMap, req.job)
    val suspend = JobUtil.areJobsSuspended(log, ddataMap)

    log.info(s"ricevuto trigger ${req.job},enabled [$enabled],suspended [$suspend]")

    val timeLimit = Util.now().minusDays(limitJobsDays)

    if (enabled && !suspend) {
      val jobTF = req.job match {
        case Jobs.FTP_UPLOAD_RETRY.name =>
          ftpRetry(req, timeLimit)
        case x =>
          Future.failed(DigitPaException(s"job $x not found", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
      }
      jobTF
        .map(_ => {
          log.info(s"job actor terminated successfully ${req.job}")
          replyTo ! TriggerJobResponse(req.sessionId, SchedulerStatus.OK, Some(s"Job triggered"), req.testCaseId)
        })
        .recover({ case e =>
          log.info(s"job actor terminated with error ${req.job},${e.getMessage}")
          replyTo ! TriggerJobResponse(req.sessionId, SchedulerStatus.KO, Some(e.getMessage), req.testCaseId)
        })
    } else {
      val jobstatus = if (suspend) { "suspended" }
      else { "disabled" }
      repositories.fdrRepository
        .insertSchedulerTrace(req.sessionId, req.job, req.cron, SchedulerStatus.WARN, Some(s"Job $jobstatus"))
        .onComplete(_ => {
          replyTo ! TriggerJobResponse(req.sessionId, SchedulerStatus.OK, Some(s"Job $jobstatus"), req.testCaseId)
        })
    }
  }

  private def ftpRetry(req: TriggerJobRequest, timeLimit: LocalDateTime): Future[TriggerJobResponse] = {
    val ftpmaxRetry = DDataChecks.getConfigurationKeys(ddataMap, "scheduler.ftpUploadRetryPollerMaxRetry").toInt
    for {
      exists <- repositories.fdrRepository.existsToRetry(ftpmaxRetry, timeLimit)
      _ <-
        if (!exists) {
          repositories.fdrRepository.insertSchedulerTrace(req.sessionId, req.job, req.cron, SchedulerStatus.OK, Some(NOTHING_TO_DO_MSG))
        } else {
          for {
            _ <- repositories.fdrRepository.insertSchedulerTrace(req.sessionId, req.job, req.cron, SchedulerStatus.OK, None)
            _ = log.debug(s"Trovati file da caricare,chiamata FtpSenderRetry")
            subreq = WorkRequest(req.sessionId, req.testCaseId, req.job)
            _ <- getRouter(req.job).ask(subreq).mapTo[WorkResponse]
          } yield ()
        }
      _ = log.info("full job completed ")
    } yield TriggerJobResponse(req.sessionId, SchedulerStatus.OK, None, req.testCaseId)
  }

}
