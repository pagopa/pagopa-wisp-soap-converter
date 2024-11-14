package it.gov.pagopa.common.util.azure.cosmos

import java.time.Instant

object EventCategory extends Enumeration {
  val INTERFACE = Value
  val INTERNAL = Value
}

object Esito extends Enumeration {
  val OK, ERROR = Value
}

case class ReEventEntity(
                          id: String,
                          partitionKey: String,
                          operationId: String,
                          insertedTimestamp: Instant,
                          eventCategory: String,
                          status: String,
                          outcome: String,
                          httpMethod: String,
                          httpUri: String,
                          httpStatusCode: Integer,
                          executionTimeMs: java.lang.Long,
                          requestHeaders: String,
                          responseHeaders: String,
                          requestPayload: String,
                          responsePayload: String,
                          businessProcess: String,
                          operationErrorCode: String,
                          operationErrorDetail: String,
                          sessionId: String,
                          cartId: String,
                          iuv: String,
                          noticeNumber: String,
                          domainId: String,
                          ccp: String,
                          psp: String,
                          station: String,
                          channel: String,
                          info: String,
                        ) {
  def getId: String = id

  def getPartitionKey: String = partitionKey

  def getOperationId: String = operationId

  def getInsertedTimestamp: Instant = insertedTimestamp

  def getCategory: String = eventCategory

  def getOutcome: String = outcome

  def getHttpMethod: String = httpMethod

  def getHttpUri: String = httpUri

  def getRequestHeaders: String = requestHeaders

  def getResponseHeaders: String = responseHeaders

  def getHttpStatusCode: Integer = httpStatusCode

  def getExecutionTimeMs: java.lang.Long = executionTimeMs

  def getRequestPayload: String = requestPayload

  def getResponsePayload: String = responsePayload

  def getBusinessProcess: String = businessProcess

  def getOperationErrorDetail: String = operationErrorDetail

  def getOperationErrorCode: String = operationErrorCode

  def getSessionId: String = sessionId

  def getCartId: String = cartId

  def getIuv: String = iuv

  def getNoticeNumber: String = noticeNumber

  def getDomainId: String = domainId

  def getCcp: String = ccp

  def getPsp: String = psp

  def getStation: String = station

  def getChannel: String = channel

  def getStatus: String = status

  def getInfo: String = info
}