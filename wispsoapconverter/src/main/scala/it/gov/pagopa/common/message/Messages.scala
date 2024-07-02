package it.gov.pagopa.common.message

trait CborSerializable

trait BaseMessage extends CborSerializable {
  val sessionId: Option[String] = None
  val testCaseId: Option[String] = None
}
