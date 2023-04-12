package eu.sia.pagopa.ftpsender.util

object FTPFailureReason extends Enumeration {
  val AUTHENTICATION, VALIDATION, CONFIGURATION, NETWORK, UNKNOWN = Value
}
