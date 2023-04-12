package eu.sia.pagopa.common.util

import java.nio.charset.{ Charset, StandardCharsets }
import java.time.format.DateTimeFormatter

object Constant {

  final val OK = "OK"
  final val KO = "KO"

  val RE_UID = "eventHubId"

  val MOD3: String = "MOD3"
  val MOD1: String = "MOD1"
  val PAYMENT_IO: String = "PAYMENT_IO"
  val RPT: String = "RPT"
  val CARRELLO: String = "CARRELLO"

  val STATION_VERSION_1 = BigDecimal(1)
  val CLOSE_PAYMENT_V1 = "v1"
  val CLOSE_PAYMENT_V2 = "v2"

  val INFORMATIVA_FULL = "FULL"
  val INFORMATIVA_EMPTY = "EMPTY"

  val Y = "Y"
  val N = "N"

  val PAYMENT = "payment_"
  val POSITION = "position_"
  val RECIPIENT = "receipt_recipient_"

  val SUFFIX_PULL = "pull"

  val HTTP_RESP_SESSION_ID_HEADER = "sessionId"
  val UNKNOWN = "UNKNOWN"

  val UTF_8: Charset = StandardCharsets.UTF_8
  val DTF_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val DTF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val TIPOVERSAMENTO_PO: String = "PO"

  val SUSPEND_JOBS_KEY = "scheduler.suspendAllJobs"

  val SERVICE_IDENTIFIER_KEY = "SERVICE_IDENTIFIER"
  val INSTANCE_KEY = "INSTANCE"
  val SERVICE_IDENTIFIER = sys.env.get(SERVICE_IDENTIFIER_KEY).getOrElse("NOT_SET")
  val INSTANCE = sys.env.get(INSTANCE_KEY).getOrElse("")

  val RE_JSON_LOG = "reJsonLog"
  val RE_XML_LOG = "reXmlLog"

  val SERVER = "SERVER"
  val CLIENT = "CLIENT"
  val REQUEST = "REQUEST"
  val RESPONSE = "RESPONSE"

  object KeyName {
    val EMPTY_KEY = "NO_KEY"
    val SOAP_INPUT = "soap-input"
    val REST_INPUT = "rest-input"
    val CONFIG = "config"
    val RE_FEEDER = "re-feeder"
    val FTP_RETRY = "ftp-retry"
    val FTP_SENDER = "ftp-sender"
    val RENDICONTAZIONI = "rendicontazioni"
    val IDEMPOTENCY = "idempotency"
    val DEAD_LETTER_MONITOR = "dead-letter-monitor"
  }

  object MDCKey {
    val SESSION_ID = "sessionId"
    val ORIGINAL_SESSION_ID = "originalSessionId"
    val ACTOR_CLASS_ID = "actorClassId"
    val PRIMITIVE = "primitive"
    val ID_STAZIONE = "idStazione"
    val ID_CANALE = "idCanale"
    val ID_CARRELLO = "idCarrello"
    val IUV = "iuv"
    val CCP = "ccp"
    val ID_DOMINIO = "idDominio"
    val PAYMENT_TOKEN = "paymentToken"
    val NOTICE_NUMBER = "noticeNumber"
    val CREDITOR_REFERENCE_ID = "creditorReferenceId"
    val DATA_ORA_EVENTO = "dataOraEvento"
    val ELAPSED = "elapsed"
    val SERVICE_IDENTIFIER = "serviceIdentifier"
  }

  object ContentType extends Enumeration {
    val XML, JSON, TEXT, MULTIPART_FORM_DATA = Value
  }

  object RequestType extends Enumeration {
    val SOAP, REST = Value
  }

  object RestReceiverType extends Enumeration {
    val PM: RestReceiverType.Value = Value
  }

  object Sftp {
    val RENDICONTAZIONI = "pushFileRendicontazioni"
  }

}
