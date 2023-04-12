package eu.sia.pagopa.common.message

import eu.sia.pagopa.common.repo.re.model.Re

import java.time.LocalDateTime

case class RestRequest(
    override val sessionId: String,
    payload: Option[String],
    queryParameters: Seq[(String, String)] = Nil,
    callRemoteAddress: String,
    primitive: String,
    timestamp: LocalDateTime,
    reExtra: ReExtra,
    override val testCaseId: Option[String] = None
) extends BaseMessage

case class RestResponse(override val sessionId: String, payload: Option[String], statusCode: Int, re: Option[Re], override val testCaseId: Option[String] = None, throwable: Option[Throwable])
    extends BaseMessage
