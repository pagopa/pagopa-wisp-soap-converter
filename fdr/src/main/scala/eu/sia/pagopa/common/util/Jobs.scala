package eu.sia.pagopa.common.util

case class Job(name: String, descr: String)
object Jobs {
  val ANNULLAMENTO_RPT_MAI_RICHIESTE_DA_PM: Job =
    Job("annullamentoRptMaiRichiesteDaPm", "Annullamento RPT mai richieste da PM")
  val FTP_UPLOAD_RETRY: Job = Job("ftpUpload", "FTP retry upload")
  val GENERA_RENDICONTAZIONI: Job = Job("generaRendicontazioneBollo", "Genera rendicontazioni bollo")
  val PA_INVIA_RT: Job = Job("paInviaRt", "PA invia RT")
  val PA_RETRY_PA_INVIA_RT_NEGATIVE: Job = Job("paRetryPaInviaRtNegative", "PA invia RT negative retry")
  val PA_INVIA_RT_RECOVERY: Job = Job("paInviaRtRecovery", "PA invia RT recovery istantanee")
  val PSP_CHIEDI_AVANZAMENTO_RPT: Job = Job("pspChiediAvanzamentoRpt", "PSP chiedi avanzamento RPT")
  val PSP_CHIEDI_LISTA_AND_CHIEDI_RT: Job = Job("pspChiediListaAndChiediRt", "PSP chiedi lista e chiedi RT")
  val PSP_RETRY_ACK_NEGATIVE: Job = Job("pspRetryAckNegative", "PSP retry ACK negative")
  val REFRESH_CONFIGURATION: Job = Job("refreshConfiguration", "Refresh configurazione")
  val RT_PULL_RECOVERY_PUSH: Job = Job("rtPullRecoveryPush", "Rt pull recovery canali push")
  val MOD3_CANCEL_PAYMENT_V1: Job = Job("mod3CancelV1", "Cancel payment MOD3 v1")
  val MOD3_CANCEL_PAYMENT_V2: Job = Job("mod3CancelV2", "Cancel payment MOD3 v2")
  val RETRY_PA_SEND_RT: Job = Job("paSendRt", "PA send RT")
  val RETRY_PA_ATTIVA_RT: Job = Job("paRetryAttivaRpt", "Retry PA attiva RPT")
  val IDEMPOTENCY_CACHE_CLEAN: Job = Job("idempotencyCacheClean", "Idempotency Cache Clean")
  val POSITION_RETRY_SENDPAYMENTRESULT: Job = Job("positionRetrySendPaymentResult", "Retry sendPaymentResult al PM")

  def descr(name: String): String = {
    toSeq.find(_._1 == name).fold(name)(_._2)
  }
  val toSeq: Seq[(String, String)] = {
    Seq(
      ANNULLAMENTO_RPT_MAI_RICHIESTE_DA_PM.name -> ANNULLAMENTO_RPT_MAI_RICHIESTE_DA_PM.descr,
      FTP_UPLOAD_RETRY.name -> FTP_UPLOAD_RETRY.descr,
      GENERA_RENDICONTAZIONI.name -> GENERA_RENDICONTAZIONI.descr,
      PA_INVIA_RT.name -> PA_INVIA_RT.descr,
      PA_RETRY_PA_INVIA_RT_NEGATIVE.name -> PA_RETRY_PA_INVIA_RT_NEGATIVE.descr,
      PSP_CHIEDI_AVANZAMENTO_RPT.name -> PSP_CHIEDI_AVANZAMENTO_RPT.descr,
      PSP_CHIEDI_LISTA_AND_CHIEDI_RT.name -> PSP_CHIEDI_LISTA_AND_CHIEDI_RT.descr,
      PSP_RETRY_ACK_NEGATIVE.name -> PSP_RETRY_ACK_NEGATIVE.descr,
      REFRESH_CONFIGURATION.name -> REFRESH_CONFIGURATION.descr,
      RT_PULL_RECOVERY_PUSH.name -> RT_PULL_RECOVERY_PUSH.descr,
      MOD3_CANCEL_PAYMENT_V1.name -> MOD3_CANCEL_PAYMENT_V1.descr,
      MOD3_CANCEL_PAYMENT_V2.name -> MOD3_CANCEL_PAYMENT_V2.descr,
      RETRY_PA_SEND_RT.name -> RETRY_PA_SEND_RT.descr,
      RETRY_PA_ATTIVA_RT.name -> RETRY_PA_ATTIVA_RT.descr,
      IDEMPOTENCY_CACHE_CLEAN.name -> IDEMPOTENCY_CACHE_CLEAN.descr,
      POSITION_RETRY_SENDPAYMENTRESULT.name -> POSITION_RETRY_SENDPAYMENTRESULT.descr,
      PA_INVIA_RT_RECOVERY.name -> PA_INVIA_RT_RECOVERY.descr
    ).sortBy(_._2)
  }
}
