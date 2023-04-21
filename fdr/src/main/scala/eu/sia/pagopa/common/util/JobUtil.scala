package eu.sia.pagopa.common.util

import akka.actor.ActorRef
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.exception.DigitPaException
import eu.sia.pagopa.common.message.{WorkRequest, WorkResponse}
import eu.sia.pagopa.common.repo.re.model.Re
import org.slf4j.MDC

import scala.util.{Failure, Try}

object JobUtil {
  def isJobEnabled(log: NodoLogger, ddataMap: ConfigData, jobName: String): Boolean = {
    Try(DDataChecks.getConfigurationKeys(ddataMap, s"scheduler.jobName_$jobName.enabled").toBoolean) //FIXME jobName_
      .recoverWith({ case e: Throwable =>
        val illegal = new IllegalArgumentException(s"La chiave [scheduler.$jobName.enabled] è obbligatoria", e)
        Failure(illegal)
      })
      .getOrElse({
        log.warn(s"Schedule job [$jobName] not enabled")
        false
      })
  }

  def areJobsSuspended(log: NodoLogger, ddataMap: ConfigData): Boolean = {
    Try(DDataChecks.getConfigurationKeys(ddataMap, Constant.SUSPEND_JOBS_KEY).toBoolean)
      .recoverWith({ case e: Throwable =>
        val illegal = new IllegalArgumentException(s"La chiave [${Constant.SUSPEND_JOBS_KEY}] è obbligatoria", e)
        Failure(illegal)
      })
      .getOrElse({
        log.warn(s"Jobs are suspended")
        true
      })
  }

  def actorError(replyTo: ActorRef, req: WorkRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    replyTo ! WorkResponse(req.sessionId, req.testCaseId, req.key, Some(dpe))
  }
}
