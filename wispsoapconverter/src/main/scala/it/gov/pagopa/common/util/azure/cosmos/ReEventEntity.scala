package it.gov.pagopa.common.util.azure.cosmos

import java.time.Instant

object Componente extends Enumeration {
  val WISP_SOAP_CONVERTER = Value
}
object CategoriaEvento extends Enumeration {
  val INTERFACCIA = Value
  val INTERNO = Value
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
  val INVIATA = Value
  val INVIATA_KO = Value
  val RICEVUTA = Value
  val RICEVUTA_KO = Value
  val NO_RICEVUTA = Value
  val CAMBIO_STATO = Value
}

case class ReEventEntity(
  id:String,
  partitionKey:String,
  requestId:String,
  operationId:String,
  clientOperationId:String,
  componente:String,
  insertedTimestamp:Instant,
  categoriaEvento:String,
  sottoTipoEvento:String,
  callType:String,
  fruitore:String,
  fruitoreDescr:String,
  erogatore:String,
  erogatoreDescr:String,
  esito:String,
  httpMethod:String,
  httpUri:String,
  httpHeaders:String,
  httpCallRemoteAddress:String,
  httpStatusCode:Integer,
  executionTimeMs:java.lang.Long,
  compressedPayload:String,
  compressedPayloadLength:Integer,
  businessProcess:String,
  operationStatus:String,
  operationErrorTitle:String,
  operationErrorDetail:String,
  operationErrorCode:String,
  idDominio:String,
  iuv:String,
  ccp:String,
  psp:String,
  tipoVersamento:String,
  tipoEvento:String,
  stazione:String,
  canale:String,
  parametriSpecificiInterfaccia:String,
  status:String,
  info:String,
  pspDescr:String,
  noticeNumber:String,
  creditorReferenceId:String,
  paymentToken:String,
  sessionIdOriginal:String,
  standIn:java.lang.Boolean
){

  def getId:String= id
  def getPartitionKey:String= partitionKey
  def getRequestId:String= requestId
  def getOperationId:String= operationId
  def getClientOperationId:String= clientOperationId
  def getComponente:String= componente
  def getInsertedTimestamp:Instant= insertedTimestamp
  def getCategoriaEvento:String= categoriaEvento
  def getSottoTipoEvento:String= sottoTipoEvento
  def getCallType:String= callType
  def getFruitore:String= fruitore
  def getFruitoreDescr:String= fruitoreDescr
  def getErogatore:String= erogatore
  def getErogatoreDescr:String= erogatoreDescr
  def getEsito:String= esito
  def getHttpMethod:String= httpMethod
  def getHttpUri:String= httpUri
  def getHttpHeaders:String= httpHeaders
  def getHttpCallRemoteAddress:String= httpCallRemoteAddress
  def getHttpStatusCode:Integer= httpStatusCode
  def getExecutionTimeMs:java.lang.Long= executionTimeMs
  def getCompressedPayload:String= compressedPayload
  def getCompressedPayloadLength:Integer= compressedPayloadLength
  def getBusinessProcess:String= businessProcess
  def getOperationStatus:String= operationStatus
  def getOperationErrorTitle:String= operationErrorTitle
  def getOperationErrorDetail:String= operationErrorDetail
  def getOperationErrorCode:String= operationErrorCode
  def getIdDominio:String= idDominio
  def getIuv:String= iuv
  def getCcp:String= ccp
  def getPsp:String= psp
  def getTipoVersamento:String= tipoVersamento
  def getTipoEvento:String= tipoEvento
  def getStazione:String= stazione
  def getCanale:String= canale
  def getParametriSpecificiInterfaccia:String= parametriSpecificiInterfaccia
  def getStatus:String= status
  def getInfo:String= info
  def getPspDescr:String= pspDescr
  def getNoticeNumber:String= noticeNumber
  def getCreditorReferenceId:String= creditorReferenceId
  def getPaymentToken:String= paymentToken
  def getSessionIdOriginal:String= sessionIdOriginal
  def getStandIn:java.lang.Boolean= standIn

}