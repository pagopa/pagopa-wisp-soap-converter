package it.gov.pagopa.common.util

import java.nio.charset.{Charset, StandardCharsets}

object Constant {

  final val OK = "OK"
  final val KO = "KO"

  val HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key"
  val RE_UID = "eventHubId"
  val UNKNOWN = "UNKNOWN"
  val HTTP_RESP_SESSION_ID_HEADER = "sessionId"
  val TIPOVERSAMENTO_PO = "PO"
  val SERVICE_IDENTIFIER_KEY = "SERVICE_IDENTIFIER"
  val RE_JSON_LOG = "reJsonLog"
  val RE_XML_LOG = "reXmlLog"
  val SERVER = "SERVER"
  val REQUEST = "REQUEST"
  val RESPONSE = "RESPONSE"

  val SERVICE_IDENTIFIER = sys.env.get(SERVICE_IDENTIFIER_KEY).getOrElse("NOT_SET")

  val UTF_8: Charset = StandardCharsets.UTF_8

  object KeyName {
    val SOAP_INPUT = "soap-input"
    val RE_FEEDER = "re-feeder"
    val DEAD_LETTER_MONITOR = "dead-letter-monitor"
  }

  object MDCKey {
    val SESSION_ID = "sessionId"
    val ACTOR_CLASS_ID = "actorClassId"
    val ID_CARRELLO = "idCarrello"
    val IUV = "iuv"
    val CCP = "ccp"
    val ID_DOMINIO = "idDominio"
    val DATA_ORA_EVENTO = "dataOraEvento"
    val ELAPSED = "elapsed"
    val SERVICE_IDENTIFIER = "serviceIdentifier"
  }

}
