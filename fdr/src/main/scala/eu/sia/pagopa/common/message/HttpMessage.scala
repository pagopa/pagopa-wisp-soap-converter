package eu.sia.pagopa.common.message

import akka.http.scaladsl.model.{HttpHeader, HttpMethod}
import eu.sia.pagopa.common.exception.DigitPaException
import eu.sia.pagopa.common.repo.re.model.Re

import scala.concurrent.duration.FiniteDuration

case class ProxyData(host: String, port: Int, username: Option[String] = None, password: Option[String] = None)

case class SimpleHttpReq(
    override val sessionId: String,
    messageType: String,
    contentype: akka.http.scaladsl.model.ContentType.NonBinary,
    method: HttpMethod,
    uri: String,
    payload: Option[String] = None,
    headers: Seq[(String, String)] = Nil,
    receiver: Option[String],
    re: Re,
    timeout: FiniteDuration,
    proxyData: Option[ProxyData],
    override val testCaseId: Option[String] = None
) extends BaseMessage
case class SimpleHttpRes(
    override val sessionId: String,
    statusCode: Int,
    headers: Seq[HttpHeader],
    payload: Option[String],
    throwable: Option[DigitPaException],
    override val testCaseId: Option[String] = None
) extends BaseMessage
