package it.gov.pagopa.common.message
import it.gov.pagopa.common.util.azure.cosmos.CallType
case class ReExtra(
                    callType:Option[CallType.Value] = None,
                    uri: Option[String] = None,
                    headers: Seq[(String, String)] = Nil,
                    httpMethod: Option[String] = None,
                    callRemoteAddress: Option[String] = None,
                    statusCode: Option[Int] = None,
                    elapsed: Option[Long] = None,
                    soapProtocol: Boolean = false
                  )

case class ReRequest(
                      override val sessionId: String,
                      override val testCaseId: Option[String] = None,
                      re: Re,
                      reExtra: Option[ReExtra] = None
                    ) extends BaseMessage
