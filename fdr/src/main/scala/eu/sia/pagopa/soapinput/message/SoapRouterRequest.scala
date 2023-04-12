package eu.sia.pagopa.soapinput.message

import eu.sia.pagopa.common.message.BaseMessage

import java.time.LocalDateTime

case class SoapRouterRequest(
    override val sessionId: String,
    payload: String,
    timestamp: LocalDateTime,
    override val testCaseId: Option[String] = None,
    uri: Option[String] = None,
    headers: Option[Seq[(String, String)]] = None,
    params: Option[Map[String, String]] = None,
    httpMethod: Option[String] = None,
    callRemoteAddress: Option[String] = None,
    soapAction: Option[String] = None
) extends BaseMessage
