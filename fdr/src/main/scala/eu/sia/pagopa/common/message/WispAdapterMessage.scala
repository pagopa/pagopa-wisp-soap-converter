package eu.sia.pagopa.common.message

case class WispAdapterChiediSceltaRequest(override val sessionId: String, identificativoDominio: String, keyPA: String, keyWISP: String, override val testCaseId: Option[String]) extends BaseMessage {}

case class WispAdapterChiediSceltaResponse(
    override val sessionId: String,
    override val testCaseId: Option[String],
    throwable: Option[Throwable] = None,
    effettuazioneScelta: Option[String] = None,
    identificativoPSP: Option[String] = None,
    identificativoIntermediarioPSP: Option[String] = None,
    identificativoCanale: Option[String] = None,
    tipoVersamento: Option[String] = None,
    reason: Option[WISPFailureReason.Value] = None
) extends BaseMessage

object WISPFailureReason extends Enumeration {
  val DB, VALIDATION, UNKNOWN = Value
}
