package it.gov.pagopa.common.util.azure.cosmos

import java.time.Instant

object Componente extends Enumeration {
  val WISP_SOAP_CONVERTER = Value
}

object CategoriaEvento extends Enumeration {
  val INTERFACE = Value
  val INTERNAL = Value
}

object SottoTipoEvento extends Enumeration {
  val REQ = Value
  val RESP = Value
  val INTERN = Value
}

object CallType extends Enumeration {
  val SERVER = Value
  val CLIENT = Value
}

object Esito extends Enumeration {
  val SEND = Value
  val SEND_FAILURE = Value
  val RECEIVED = Value
  val RECEIVED_FAILURE = Value
  val NEVER_RECEIVED = Value
  val EXCECUTED_INTERNAL_STEP = Value
}

case class ReEventEntity(
                          id: String,
                          partitionKey: String,
                          requestId: String,
                          operationId: String,
                          clientOperationId: String,
                          component: String,
                          insertedTimestamp: Instant,
                          eventCategory: String,
                          eventSubcategory: String,
                          callType: String,
                          outcome: String,
                          httpMethod: String,
                          httpUri: String,
                          httpHeaders: String,
                          httpCallRemoteAddress: String,
                          httpStatusCode: Integer,
                          executionTimeMs: java.lang.Long,
                          compressedPayload: String,
                          compressedPayloadLength: Integer,
                          businessProcess: String,
                          operationStatus: String,
                          operationErrorTitle: String,
                          operationErrorDetail: String,
                          operationErrorCode: String,
                          primitive: String,
                          sessionId: String,
                          cartId: String,
                          iuv: String,
                          noticeNumber: String,
                          domainId: String,
                          ccp: String,
                          psp: String,
                          station: String,
                          channel: String,
                          status: String,
                          info: String,
                        ) {
  def getId: String = id

  def getPartitionKey: String = partitionKey

  def getRequestId: String = requestId

  def getOperationId: String = operationId

  def getClientOperationId: String = clientOperationId

  def getComponent: String = component

  def getInsertedTimestamp: Instant = insertedTimestamp

  def getEventCategory: String = eventCategory

  def getEventSubcategory: String = eventSubcategory

  def getCallType: String = callType

  def getOutcome: String = outcome

  def getHttpMethod: String = httpMethod

  def getHttpUri: String = httpUri

  def getHttpHeaders: String = httpHeaders

  def getHttpCallRemoteAddress: String = httpCallRemoteAddress

  def getHttpStatusCode: Integer = httpStatusCode

  def getExecutionTimeMs: java.lang.Long = executionTimeMs

  def getCompressedPayload: String = compressedPayload

  def getCompressedPayloadLength: Integer = compressedPayloadLength

  def getBusinessProcess: String = businessProcess

  def getOperationStatus: String = operationStatus

  def getOperationErrorTitle: String = operationErrorTitle

  def getOperationErrorDetail: String = operationErrorDetail

  def getOperationErrorCode: String = operationErrorCode

  def getPrimitive: String = primitive

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