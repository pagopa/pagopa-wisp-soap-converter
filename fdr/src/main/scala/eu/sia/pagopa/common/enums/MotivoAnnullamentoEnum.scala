package eu.sia.pagopa.common.enums

object MotivoAnnullamentoEnum extends Enumeration {
  val CONPSP, RIFPSP, UNKPSP, AUTNEG, ANNUTE, SESSCA, OTHER, NA, ESEGUITO, KO, TOKEN_SCADUTO, ACT_ERROR = Value

  def description(value: Option[MotivoAnnullamentoEnum.Value]) = {
    value match {
      case None | Some(NA) => "Annullato da batch nodo"
      case Some(CONPSP) =>
        "Annullato per errore in connessione"
      case Some(RIFPSP) =>
        "Annullato per RPT rifiutata"
      case Some(UNKPSP) =>
        "Annullato per esito sconosciuto PSP"
      case Some(AUTNEG) =>
        "Autorizzazione negata"
      case Some(ANNUTE) =>
        "Annullato da utente"
      case Some(SESSCA) =>
        "Annullato per sessione scaduta"
      case Some(OTHER) =>
        "Annullato da WISP"
      case Some(ESEGUITO) =>
        "ESEGUITO"
      case Some(KO) =>
        "SENDPAYMENTOUTCOME:KO"
      case Some(TOKEN_SCADUTO) =>
        "BATCH_ANNULLAMENTO:TOKEN_SCADUTO"
      case Some(ACT_ERROR) =>
        "ERRORE IN FASE DI ATTIVAZIONE"
      case Some(_) =>
        "Annullato da WISP"
    }
  }

}
