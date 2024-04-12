package it.gov.pagopa.common.message

import java.time.Instant

case class SoapRequest(
    override val sessionId: String,
    payload: String,
    callRemoteAddress: String,
    primitive: String,
    sender: String,
    timestamp: Instant,
    reExtra: ReExtra,
    idempotency: Boolean,
    override val testCaseId: Option[String] = None
) extends BaseMessage

case class SoapResponse(override val sessionId: String, payload: String, statusCode: Int, re: Option[Re], override val testCaseId: Option[String] = None, throwable: Option[Throwable] = None)
    extends BaseMessage
