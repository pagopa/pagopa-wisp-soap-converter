package it.gov.pagopa.soapinput.message

import it.gov.pagopa.common.message.BaseMessage

import java.time.Instant

case class SoapRouterRequest(
                              override val sessionId: Option[String],
                              payload: String,
                              timestamp: Instant,
                              override val testCaseId: Option[String] = None,
                              uri: Option[String] = None,
                              headers: Option[Seq[(String, String)]] = None,
                              params: Option[Map[String, String]] = None,
                              httpMethod: Option[String] = None,
                              callRemoteAddress: Option[String] = None,
                              soapAction: Option[String] = None
                            ) extends BaseMessage
