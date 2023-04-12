package eu.sia.pagopa.common.repo.offline.model

import eu.sia.pagopa.common.repo.offline.enums.SchedulerFireCheckStatus

import java.time.LocalDateTime

case class SchedulerFireCheck(id: Long, jobName: String, extraKey: String, start: LocalDateTime, status: SchedulerFireCheckStatus.Value, end: Option[LocalDateTime] = None)
