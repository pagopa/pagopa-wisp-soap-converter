package it.gov.pagopa.common.util

import java.nio.charset.{Charset, StandardCharsets}

object Constant {

  val HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key"

  final val OK = "OK"
  final val KO = "KO"

  val HTTP_RESP_SESSION_ID_HEADER = "sessionId"

  val UTF_8: Charset = StandardCharsets.UTF_8

  val TIPOVERSAMENTO_PO: String = "PO"

  val SERVICE_IDENTIFIER_KEY = "SERVICE_IDENTIFIER"

  val SERVICE_IDENTIFIER = sys.env.get(SERVICE_IDENTIFIER_KEY).getOrElse("NOT_SET")

  object KeyName {
    val SOAP_INPUT = "soap-input"
    val RE_FEEDER = "re-feeder"
    val DEAD_LETTER_MONITOR = "dead-letter-monitor"
  }

  object MDCKey {
    val SESSION_ID = "sessionId"
    val ORIGINAL_SESSION_ID = "originalSessionId"
    val ACTOR_CLASS_ID = "actorClassId"
    val ID_CARRELLO = "idCarrello"
    val IUV = "iuv"
    val CCP = "ccp"
    val ID_DOMINIO = "idDominio"
    val SERVICE_IDENTIFIER = "serviceIdentifier"
  }

}
