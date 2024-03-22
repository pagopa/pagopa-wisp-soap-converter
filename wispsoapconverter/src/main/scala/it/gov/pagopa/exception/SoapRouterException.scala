package it.gov.pagopa.exception

object SoapRouterException {
  def apply(message: String, statusCode: Int, faultcode: String, faultstring: String, detail: Option[String]): SoapRouterException = {
    SoapRouterException(message, None.orNull, statusCode, faultcode, faultstring, detail)
  }
}

case class SoapRouterException(message: String, cause: Throwable, statusCode: Int, faultcode: String, faultstring: String, detail: Option[String]) extends Exception(message, cause)
