package eu.sia.pagopa.common.message

case class FTPRequest(override val sessionId: String, override val testCaseId: Option[String], messageType: String, destinationPath: String, filename: String, fileId: Long, ftpServerId: Long)
    extends BaseMessage

case class FTPResponse(override val sessionId: String, override val testCaseId: Option[String], throwable: Option[Throwable]) extends BaseMessage
