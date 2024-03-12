package it.gov.pagopa.common.exception

import it.gov.pagopa.common.util.FaultId

import scala.language.implicitConversions

case class DigitPaException(
    message: String,
    code: DigitPaErrorCodes.Value,
    cause: Throwable = None.orNull,
    reporterId: String = FaultId.NODO_DEI_PAGAMENTI_SPC,
    originalFaultCode: Option[String] = None,
    originalFaultString: Option[String] = None,
    originalFaultDescription: Option[String] = None,
    customFaultString: Option[String] = None
) extends Exception(message, cause) {

  def this(code: DigitPaErrorCodes.Value) = this(DigitPaErrorCodes.description(code), code)

  val faultCode: String = code.toString
  val faultString: String = customFaultString.getOrElse(DigitPaErrorCodes.description(code))

  override def getMessage: String = if (Option(message).isEmpty) super.getMessage else message
}

object DigitPaException {
  def apply(code: DigitPaErrorCodes.Value, cause: Throwable): DigitPaException =
    DigitPaException(
      message = DigitPaErrorCodes.description(code),
      code = code,
      cause = cause,
      reporterId = FaultId.NODO_DEI_PAGAMENTI_SPC,
      originalFaultCode = None,
      originalFaultString = None,
      originalFaultDescription = None
    )
  def apply(code: DigitPaErrorCodes.Value): DigitPaException =
    DigitPaException(
      message = DigitPaErrorCodes.description(code),
      code = code,
      cause = None.orNull,
      reporterId = FaultId.NODO_DEI_PAGAMENTI_SPC,
      originalFaultCode = None,
      originalFaultString = None,
      originalFaultDescription = None
    )
  def apply(message: String, code: DigitPaErrorCodes.Value, reporterId: String): DigitPaException =
    DigitPaException(message = message, code = code, cause = None.orNull, reporterId = reporterId, originalFaultCode = None, originalFaultString = None, originalFaultDescription = None)
  def apply(
      message: String,
      code: DigitPaErrorCodes.Value,
      reporterId: String,
      originalFaultCode: Option[String],
      originalFaultString: Option[String],
      originalFaultDescription: Option[String]
  ): DigitPaException =
    DigitPaException(
      message = message,
      code = code,
      cause = None.orNull,
      reporterId = reporterId,
      originalFaultCode = originalFaultCode,
      originalFaultString = originalFaultString,
      originalFaultDescription = originalFaultDescription
    )
  def apply(message: String, code: DigitPaErrorCodes.Value, originalFaultCode: Option[String], originalFaultString: Option[String], originalFaultDescription: Option[String]): DigitPaException =
    DigitPaException(
      message = message,
      code = code,
      cause = None.orNull,
      reporterId = FaultId.NODO_DEI_PAGAMENTI_SPC,
      originalFaultCode = originalFaultCode,
      originalFaultString = originalFaultString,
      originalFaultDescription = originalFaultDescription
    )
  def apply(message: String, code: DigitPaErrorCodes.Value, reporterId: String, customFaultString: String): DigitPaException =
    DigitPaException(
      message = message,
      code = code,
      cause = None.orNull,
      reporterId = reporterId,
      customFaultString = Some(customFaultString)
    )
}
