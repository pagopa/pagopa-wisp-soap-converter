package it.gov.pagopa.common.enums

// format: off
object WorkflowStatus extends Enumeration {
  val //RPT_RICEVUTA_NODO, CART_RICEVUTO_NODO, // SYNTAX_CHECK_PASSED
  //RPT_ACCETTATA_NODO, CART_ACCETTATO_NODO, // SEMANTIC_CHECK_PASSED
  //RPT_RIFIUTATA_NODO, CART_RIFIUTATO_NODO, // SEMANTIC_CHECK_FAILED
  RECEIVED,
  //RPT_PARCHEGGIATA_NODO, CART_PARCHEGGIATO_NODO, // RPT_STORED

  SYNTAX_CHECK_PASSED,
  SEMANTIC_CHECK_PASSED,
  SEMANTIC_CHECK_FAILED,
  RPT_STORED

  = Value
}
