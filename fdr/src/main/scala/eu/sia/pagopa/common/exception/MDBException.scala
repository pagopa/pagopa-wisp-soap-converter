package eu.sia.pagopa.common.exception

case class MDBException(msg: String, cause: Throwable = None.orNull) extends Exception(msg, cause)
