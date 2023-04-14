package eu.sia.pagopa.common.repo.fdr.table

import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.fdr.FdrMapping
import eu.sia.pagopa.common.repo.fdr.enums.SchedulerFireCheckStatus
import eu.sia.pagopa.common.repo.fdr.model.SchedulerFireCheck

import java.time.LocalDateTime

trait SchedulerFireCheckTable { self: DBComponent with FdrMapping =>

  import driver.api._

  class SchedulerFireCheckTable(tag: Tag) extends Table[SchedulerFireCheck](tag, adjustName("SCHEDULER_FIRE_CHECK")) {
    def id = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def jobName = column[String](adjustName("JOB_NAME"))
    def extraKey = column[String](adjustName("EXTRA_KEY"))
    def start = column[LocalDateTime](adjustName("START"))
    def status = column[SchedulerFireCheckStatus.Value](adjustName("STATUS"))
    def end = column[Option[LocalDateTime]](adjustName("END"))

    override def * =
      (id, jobName, extraKey, start, status, end) <> (SchedulerFireCheck.tupled, SchedulerFireCheck.unapply)
  }

  def schedulerFireCheck = TableQuery[SchedulerFireCheckTable]
}
