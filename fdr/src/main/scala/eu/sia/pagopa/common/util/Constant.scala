package eu.sia.pagopa.common.util

import java.nio.charset.{Charset, StandardCharsets}
import java.time.format.DateTimeFormatter

object Constant {

  final val OK = "OK"
  final val KO = "KO"

  val FDR_VERSION = "FDR001"

  val HTTP_RESP_SESSION_ID_HEADER = "sessionId"

  val UTF_8: Charset = StandardCharsets.UTF_8
  val DTF_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  val SUSPEND_JOBS_KEY = "scheduler.suspendAllJobs"

  val INSTANCE_KEY = "INSTANCE"
  val APP_NAME_KEY = "APP_NAME"
  val APP_VERSION_KEY = "APP_VERSION"
  val INSTANCE = sys.env.getOrElse(INSTANCE_KEY, "")
  val APP_NAME = sys.env.getOrElse(APP_NAME_KEY, "")
  val APP_VERSION = sys.env.getOrElse(APP_VERSION_KEY, "")

  val HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key"

  val RE_JSON_LOG = "reJsonLog"
  val RE_XML_LOG = "reXmlLog"

  val UNKNOWN = "UNKNOWN"

  val RE_UID = "eventHubId"

  val SERVER = "SERVER"
  val CLIENT = "CLIENT"
  val REQUEST = "REQUEST"
  val RESPONSE = "RESPONSE"

  object KeyName {
    val EMPTY_KEY = "NO_KEY"
    val SOAP_INPUT = "soap-input"
    val REST_INPUT = "rest-input"
    val RE_FEEDER = "re-feeder"
    val FTP_RETRY = "ftp-retry"
    val FTP_SENDER = "ftp-sender"
    val RENDICONTAZIONI = "rendicontazioni"
    val DEAD_LETTER_MONITOR = "dead-letter-monitor"
  }

  object HttpStatusDescription {
    val INTERNAL_SERVER_ERROR  = "Errore generico"
    val BAD_REQUEST           = "Richiesta non valida"
  }

  object MDCKey {
    val SESSION_ID = "sessionId"
    val ACTOR_CLASS_ID = "actorClassId"
    val DATA_ORA_EVENTO = "dataOraEvento"
    val ELAPSED = "elapsed"
  }

  object ContentType extends Enumeration {
    val XML, JSON, TEXT, MULTIPART_FORM_DATA = Value
  }

  object Sftp {
    val RENDICONTAZIONI = "pushFileRendicontazioni"
  }

}
