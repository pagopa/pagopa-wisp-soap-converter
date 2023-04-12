package eu.sia.pagopa.mains

import eu.sia.pagopa.Main

object MainTestJob extends App with MainTestUtil {

  object Testjobs extends Enumeration {
    val annullamentoRptMaiRichiesteDaPm, ftpUpload, generaRendicontazioneBollo, paInviaRt, paInviaRtRecovery, paRetryPaInviaRtNegative, pspChiediAvanzamentoRpt, pspChiediListaAndChiediRt,
        pspRetryAckNegative, rtPullRecoveryPush, mod3CancelV1, mod3CancelV2, paSendRt, paRetryAttivaRpt, idempotencyCacheClean, positionRetrySendPaymentResult = Value
  }

  override def host = "127.0.0.1"

  Main.main(Array(Testjobs.paInviaRt.toString))
}
