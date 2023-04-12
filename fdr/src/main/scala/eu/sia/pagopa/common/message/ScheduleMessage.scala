package eu.sia.pagopa.common.message

case class ScheduleJobRequest(override val sessionId: String, cron: Option[String], override val testCaseId: Option[String] = None) extends BaseMessage

case class ScheduleJobResponse(override val sessionId: String, schedulerStatus: SchedulerStatus.Value, schedulerMessage: Option[String] = None, override val testCaseId: Option[String] = None)
    extends BaseMessage

case class TriggerJobRequest(override val sessionId: String, job: String, cron: Option[String], override val testCaseId: Option[String] = None) extends BaseMessage

case class TriggerJobResponse(override val sessionId: String, schedulerStatus: SchedulerStatus.Value, schedulerMessage: Option[String] = None, override val testCaseId: Option[String] = None)
    extends BaseMessage
