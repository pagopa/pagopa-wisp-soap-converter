package eu.sia.pagopa.common.message

case class WorkRequest(override val sessionId: String, override val testCaseId: Option[String], jobName: String, key: Option[String] = None, actualJobName: Option[String] = None) extends BaseMessage

case class WorkResponse(override val sessionId: String, override val testCaseId: Option[String], key: Option[String] = None, throwable: Option[Throwable] = None) extends BaseMessage
