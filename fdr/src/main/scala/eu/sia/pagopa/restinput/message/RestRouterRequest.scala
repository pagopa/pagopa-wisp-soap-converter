package eu.sia.pagopa.restinput.message

import eu.sia.pagopa.common.message._

import java.time.LocalDateTime

case class RestRouterRequest(
                              override val sessionId: String,
                              payload: Option[String],
                              timestamp: LocalDateTime,
                              override val testCaseId: Option[String] = None,
                              uri: Option[String] = None,
                              headers: Seq[(String, String)] = Nil,
                              queryParams: Seq[(String, String)] = Nil,
                              pathParams: Map[String, String],
                              httpMethod: Option[String] = None,
                              callRemoteAddress: Option[String] = None,
                              primitiva: String,
                              queryString: Option[String] = None
                            ) extends BaseMessage
