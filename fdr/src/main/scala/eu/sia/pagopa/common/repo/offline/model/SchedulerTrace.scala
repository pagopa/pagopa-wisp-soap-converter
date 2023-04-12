package eu.sia.pagopa.common.repo.offline.model

import eu.sia.pagopa.common.message.SchedulerStatus
import eu.sia.pagopa.common.repo.offline.enums.SchedulerFire

import java.time.LocalDateTime

case class SchedulerTrace(
    id: Long,
    sessionId: String,
    jobName: String,
    start: LocalDateTime,
    fire: SchedulerFire.Value,
    cron: Option[String],
    status: Option[SchedulerStatus.Value],
    message: Option[String]
)
