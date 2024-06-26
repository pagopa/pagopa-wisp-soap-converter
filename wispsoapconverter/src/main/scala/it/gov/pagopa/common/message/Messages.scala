package it.gov.pagopa.common.message

trait CborSerializable

trait BaseMessage extends CborSerializable {
  val sessionId: String
  val testCaseId: Option[String] = None
}
