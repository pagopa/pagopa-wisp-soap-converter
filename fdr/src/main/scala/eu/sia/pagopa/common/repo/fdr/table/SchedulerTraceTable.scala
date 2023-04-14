package eu.sia.pagopa.common.repo.fdr.table

import eu.sia.pagopa.common.message.SchedulerStatus
import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.fdr.FdrMapping
import eu.sia.pagopa.common.repo.fdr.enums.SchedulerFire
import eu.sia.pagopa.common.repo.fdr.model.SchedulerTrace

import java.time.LocalDateTime

trait SchedulerTraceTable { self: DBComponent with FdrMapping =>

  import driver.api._

  class SchedulerTraceTable(tag: Tag) extends Table[SchedulerTrace](tag, adjustName("SCHEDULER_TRACE")) {
    def id = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def sessionId = column[String](adjustName("ID_SESSIONE"))
    def jobName = column[String](adjustName("JOB_NAME"))
    def start = column[LocalDateTime](adjustName("START"))
    def fire = column[SchedulerFire.Value](adjustName("FIRE"))
    def cron = column[Option[String]](adjustName("CRON"))
    def status = column[Option[SchedulerStatus.Value]](adjustName("STATUS"))
    def message = column[Option[String]](adjustName("MESSAGE"))

    override def * =
      (id, sessionId, jobName, start, fire, cron, status, message) <> (SchedulerTrace.tupled, SchedulerTrace.unapply)
  }

  def schedulerTrace = TableQuery[SchedulerTraceTable]
}
