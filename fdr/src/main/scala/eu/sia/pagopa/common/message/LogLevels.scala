package eu.sia.pagopa.common.message

case class SetLogLevel(packageName: String, level: String)
case class GetLogLevel(packageName: String)
