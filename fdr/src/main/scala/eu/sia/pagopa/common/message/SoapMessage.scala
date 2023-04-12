package eu.sia.pagopa.common.message

import eu.sia.pagopa.common.repo.re.model.Re

import java.time.LocalDateTime

case class SoapRequest(
    override val sessionId: String,
    payload: String,
    callRemoteAddress: String,
    primitive: String,
    sender: String,
    timestamp: LocalDateTime,
    reExtra: ReExtra,
    override val testCaseId: Option[String] = None
) extends BaseMessage

case class SoapResponse(override val sessionId: String, payload: Option[String], statusCode: Int, re: Option[Re], override val testCaseId: Option[String] = None, throwable: Option[Throwable])
    extends BaseMessage
