package eu.sia.pagopa.common.repo.offline.enums

object RendicontazioneBolloStatus extends Enumeration {
  val NEW: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.new")
  val GENERATED: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.generated")
  val VERIFIED: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.verified")
  val STORED: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.stored")
  val SENT: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.sent")
  val ERROR: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.error")
  val ERROR_SEND: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.errorSend")
  val ACK_KO_APPL: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.ackKoApp")
  val ACK_KO_TELEM: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.ackKoTelem")
  val ACK_OK: RendicontazioneBolloStatus.Value = Value("bollo.rendicontazione.status.ackOk")
}
