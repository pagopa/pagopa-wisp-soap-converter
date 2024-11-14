package it.gov.pagopa.common.enums

// format: off
object WorkflowStatus extends Enumeration {
  val SYNTAX_CHECK_PASSED, SYNTAX_CHECK_FAILED, SEMANTIC_CHECK_PASSED, SEMANTIC_CHECK_FAILED, RPT_STORED, TRIGGER_PRIMITIVE_PROCESSED = Value
}
