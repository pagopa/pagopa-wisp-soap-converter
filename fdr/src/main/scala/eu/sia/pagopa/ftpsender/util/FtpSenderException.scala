package eu.sia.pagopa.ftpsender.util

case class FtpSenderException(message: String, code: FTPFailureReason.Value, cause: Throwable = None.orNull) extends Exception(message, cause) {}
