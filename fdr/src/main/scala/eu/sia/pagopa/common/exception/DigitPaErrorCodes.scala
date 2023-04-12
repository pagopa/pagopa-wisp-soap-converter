package eu.sia.pagopa.common.exception

import scala.language.implicitConversions

// format: off
object DigitPaErrorCodes extends Enumeration {
  val PPT_SYSTEM_ERROR,
      PPT_AUTORIZZAZIONE,
      PPT_AUTENTICAZIONE,
      PPT_SINTASSI_XSD,
      PPT_SINTASSI_EXTRAXSD,
      PPT_SEMANTICA,
      PPT_RPT_DUPLICATA,
      PPT_RPT_SCONOSCIUTA,
      PPT_RT_SCONOSCIUTA,
      PPT_RT_NONDISPONIBILE,
      PPT_SUPERAMENTOSOGLIA,
      PPT_SEGREGAZIONE,
      PPT_TIPOFIRMA_SCONOSCIUTO,
      PPT_ERRORE_FORMATO_BUSTA_FIRMATA,
      PPT_FIRMA_INDISPONIBILE,
      PPT_ID_CARRELLO_DUPLICATO,
      PPT_WISP_TIMEOUT_RECUPERO_SCELTA,
      PPT_WISP_SESSIONE_SCONOSCIUTA,
      PPT_CANALE_ERRORE,
      PPT_CODIFICA_PSP_SCONOSCIUTA,
      PPT_PSP_SCONOSCIUTO,
      PPT_PSP_DISABILITATO,
      PPT_TIPO_VERSAMENTO_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PSP_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PSP_DISABILITATO,
      PPT_CANALE_IRRAGGIUNGIBILE,
      PPT_CANALE_SERVIZIO_NONATTIVO,
      PPT_CANALE_TIMEOUT,
      PPT_CANALE_NONRISOLVIBILE,
      PPT_CANALE_INDISPONIBILE,
      PPT_CANALE_SCONOSCIUTO,
      PPT_CANALE_DISABILITATO,
      PPT_CANALE_ERR_PARAM_PAG_IMM,
      PPT_CANALE_ERRORE_RESPONSE,
      PPT_RT_DUPLICATA,
      PPT_ISCRIZIONE_NON_PRESENTE,
      PPT_ULTERIORE_ISCRIZIONE,
      PPT_PDD_IRRAGGIUNGIBILE,
      PPT_ERRORE_EMESSO_DA_PAA,
      PPT_DOMINIO_SCONOSCIUTO,
      PPT_DOMINIO_DISABILITATO,
      PPT_INTERMEDIARIO_PA_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PA_DISABILITATO,
      PPT_STAZIONE_INT_PA_IRRAGGIUNGIBILE,
      PPT_STAZIONE_INT_PA_SERVIZIO_NONATTIVO,
      PPT_STAZIONE_INT_PA_ERRORE_RESPONSE,
      PPT_STAZIONE_INT_PA_TIMEOUT,
      PPT_STAZIONE_INT_PA_NONRISOLVIBILE,
      PPT_STAZIONE_INT_PA_INDISPONIBILE,
      PPT_STAZIONE_INT_PA_SCONOSCIUTA,
      PPT_STAZIONE_INT_PA_DISABILITATA,
      PPT_ID_FLUSSO_SCONOSCIUTO,
      PAA_ID_DOMINIO_ERRATO,
      PAA_ID_INTERMEDIARIO_ERRATO,
      PAA_STAZIONE_INT_ERRATA,
      PAA_RPT_SCONOSCIUTA,
      PAA_RT_DUPLICATA,
      PAA_TIPOFIRMA_SCONOSCIUTO,
      PAA_ERRORE_FORMATO_BUSTA_FIRMATA,
      PAA_FIRMA_INDISPONIBILE,
      PAA_FIRMA_ERRATA,
      PAA_PAGAMENTO_SCONOSCIUTO,
      PAA_PAGAMENTO_DUPLICATO,
      PAA_PAGAMENTO_IN_CORSO,
      PAA_PAGAMENTO_ANNULLATO,
      PAA_PAGAMENTO_SCADUTO,
      PAA_SINTASSI_XSD,
      PAA_SINTASSI_EXTRAXSD,
      PAA_SEMANTICA,
      PAA_ATTIVA_RPT_IMPORTO_NON_VALIDO,
      CANALE_SINTASSI_XSD,
      CANALE_SINTASSI_EXTRAXSD,
      CANALE_SEMANTICA,
      CANALE_RPT_DUPLICATA,
      CANALE_RPT_SCONOSCIUTA,
      CANALE_RPT_RIFIUTATA,
      CANALE_RT_SCONOSCIUTA,
      CANALE_RT_NON_DISPONIBILE,
      CANALE_INDISPONIBILE,
      CANALE_RICHIEDENTE_ERRATO,
      CANALE_SYSTEM_ERROR,
      PPT_LOCAL_ERROR_PROTOCOLLO,
      PPT_LOCAL_ERROR_EXTRAXSD,
      PPT_LOCAL_ERROR_FSM,
      PPT_RT_SEGNO_DISCORDE,
      OGGETTO_DISABILITATO,
      PPT_IBAN_NON_CENSITO,
      PPT_RT_IN_GESTIONE,
      CANALE_CONVENZIONE_NON_VALIDA,
      PAA_CONVENZIONE_NON_VALIDA,
      PPT_TOKEN_SCONOSCIUTO,
      PPT_TOKEN_SCADUTO,
      PPT_TOKEN_SCADUTO_KO,
      PPT_PAGAMENTO_IN_CORSO,
      PPT_PAGAMENTO_DUPLICATO,
      PPT_PAGAMENTO_SCONOSCIUTO,
      PPT_ESITO_GIA_ACQUISITO,
      PPT_MULTI_BENEFICIARIO,
      PPT_ERRORE,
      PPT_ERRORE_IDEMPOTENZA,
      PPT_IBAN_ACCREDITO,
      PPT_SERVIZIO_NONATTIVO,
      PPT_SERVIZIO_SCONOSCIUTO,
      PPT_VERSIONE_SERVIZIO,
      PPT_SOAPACTION_ERRATA = Value

  //noinspection ScalaStyle
  def description(value: DigitPaErrorCodes.Value): String = value match {
    case PPT_SYSTEM_ERROR                       => "Errore generico."
    case PPT_AUTORIZZAZIONE                     => "Il richiedente non ha i diritti per l'operazione."
    case PPT_AUTENTICAZIONE                     => "Errore di autenticazione."
    case PPT_SINTASSI_XSD                       => "Errore di sintassi XSD."
    case PPT_SINTASSI_EXTRAXSD                  => "Errore di sintassi extra XSD."
    case PPT_SEMANTICA                          => "Errore semantico."
    case PPT_RPT_DUPLICATA                      => "RPT duplicata."
    case PPT_RPT_SCONOSCIUTA                    => "RPT sconosciuta."
    case PPT_RT_SCONOSCIUTA                     => "RT sconosciuta."
    case PPT_RT_NONDISPONIBILE                  => "RT non ancora pronta."
    case PPT_SUPERAMENTOSOGLIA                  => "Una qualche soglia fissata per PPT e' temporaneamente superata e la richiesta e' quindi rifiutata."
    case PPT_SEGREGAZIONE                       => "La richiesta riguarda RPT/RT non di competenza del richiedente."
    case PPT_TIPOFIRMA_SCONOSCIUTO              => "Il campo tipoFirma non corrisponde ad alcun valore previsto."
    case PPT_ERRORE_FORMATO_BUSTA_FIRMATA       => "Formato busta di firma errato o non corrispondente al tipoFirma."
    case PPT_FIRMA_INDISPONIBILE                => "Impossibile firmare."
    case PPT_ID_CARRELLO_DUPLICATO              => "Identificativo Carrello RPT duplicato."
    case PPT_WISP_TIMEOUT_RECUPERO_SCELTA       => "Chiave relativa ad una scelta scaduta."
    case PPT_WISP_SESSIONE_SCONOSCIUTA          => "Chiave non corrispondente ad alcuna sessione di scelta tramite WISP."
    case PPT_CANALE_ERRORE                      => "Errore restituito dal Canale."
    case PPT_CODIFICA_PSP_SCONOSCIUTA           => "Valore di codificaInfrastruttura PSP non censito."
    case PPT_PSP_SCONOSCIUTO                    => "PSP sconosciuto."
    case PPT_PSP_DISABILITATO                   => "PSP conosciuto ma disabilitato da configurazione."
    case PPT_TIPO_VERSAMENTO_SCONOSCIUTO        => "Identificativo tipo versamento sconosciuto."
    case PPT_INTERMEDIARIO_PSP_SCONOSCIUTO      => "Identificativo intermediario psp sconosciuto."
    case PPT_INTERMEDIARIO_PSP_DISABILITATO     => "Intermediario psp disabilitato."
    case PPT_CANALE_IRRAGGIUNGIBILE             => "Errore di connessione verso il Canale."
    case PPT_CANALE_SERVIZIO_NONATTIVO          => "Il Servizio Applicativo del Canale non e' attivo."
    case PPT_CANALE_TIMEOUT                     => "Timeout risposta dal Canale."
    case PPT_CANALE_NONRISOLVIBILE              => "Il canale non e' specificato,e nessun canale risulta utilizzabile secondo configurazione."
    case PPT_CANALE_INDISPONIBILE               => "Nessun canale utilizzabile e abilitato."
    case PPT_CANALE_SCONOSCIUTO                 => "Canale sconosciuto."
    case PPT_CANALE_DISABILITATO                => "Canale conosciuto ma disabilitato da configurazione."
    case PPT_CANALE_ERR_PARAM_PAG_IMM           => "Parametri restituiti dal Canale per identificare il pagamento non corretti."
    case PPT_CANALE_ERRORE_RESPONSE             => "La response ricevuta dal Canale e' vuota o non corretta sintatticamente o semanticamente."
    case PPT_PDD_IRRAGGIUNGIBILE                => "Errore di connessione verso la PDD."
    case PPT_ERRORE_EMESSO_DA_PAA               => "Errore restituito dalla PAA."
    case PPT_DOMINIO_SCONOSCIUTO                => "Identificativo Dominio sconosciuto."
    case PPT_DOMINIO_DISABILITATO               => "Dominio disabilitato."
    case PPT_INTERMEDIARIO_PA_SCONOSCIUTO       => "Identificativo intermediario dominio sconosciuto."
    case PPT_INTERMEDIARIO_PA_DISABILITATO      => "Intermediario dominio disabilitato."
    case PPT_STAZIONE_INT_PA_IRRAGGIUNGIBILE    => "Errore di connessione verso la Stazione."
    case PPT_STAZIONE_INT_PA_SERVIZIO_NONATTIVO => "Il Servizio Applicativo della Stazione non e' attivo."
    case PPT_STAZIONE_INT_PA_TIMEOUT            => "Timeout risposta dalla Stazione."
    case PPT_STAZIONE_INT_PA_NONRISOLVIBILE     => "La Stazione non e' specificata e nessuna stazione risulta utilizzabile secondo configurazione."
    case PPT_STAZIONE_INT_PA_INDISPONIBILE      => "Nessuna stazione utilizzabile e abilitata."
    case PPT_STAZIONE_INT_PA_SCONOSCIUTA        => "IdentificativoStazioneRichiedente sconosciuto."
    case PPT_STAZIONE_INT_PA_DISABILITATA       => "Stazione disabilitata."
    case PPT_MULTI_BENEFICIARIO                 => "La chiamata non è compatibile con il nuovo modello PSP."
    case PPT_ID_FLUSSO_SCONOSCIUTO              => "Identificativo flusso sconosciuto."
    case PPT_ISCRIZIONE_NON_PRESENTE            => "Iscrizione canale non trovata."
    case PPT_ULTERIORE_ISCRIZIONE               => "Iscrizione canale già presente."
    case PPT_IBAN_NON_CENSITO                   => "Il codice IBAN indicato dal EC non è presente nella lista degli IBAN comunicati al sistema pagoPA."
    case PAA_ID_DOMINIO_ERRATO                  => "La PAA non corrisponde al Dominio indicato."
    case PAA_ID_INTERMEDIARIO_ERRATO            => "Identificativo intermediario non corrispondente."
    case PAA_STAZIONE_INT_ERRATA                => "Stazione intermediario non corrispondente."
    case PAA_RPT_SCONOSCIUTA                    => "La RPT risulta sconosciuta."
    case PAA_RT_DUPLICATA                       => "La RT e' gia' presente."
    case PAA_TIPOFIRMA_SCONOSCIUTO              => "Il campo tipoFirma non corrisponde ad alcun valore previsto."
    case PAA_ERRORE_FORMATO_BUSTA_FIRMATA       => "Formato busta di firma errato o non corrispondente al tipoFirma."
    case PAA_FIRMA_INDISPONIBILE                => "Impossibile firmare."
    case PAA_FIRMA_ERRATA                       => "Errore di firma."
    case PAA_PAGAMENTO_SCONOSCIUTO              => "Pagamento in attesa risulta sconosciuto alla PAA."
    case PAA_PAGAMENTO_DUPLICATO                => "Pagamento in attesa risulta concluso alla PAA."
    case PAA_PAGAMENTO_IN_CORSO                 => "Pagamento in attesa risulta in corso alla PAA."
    case PAA_PAGAMENTO_ANNULLATO                => "Pagamento in attesa risulta annullato alla PAA."
    case PAA_PAGAMENTO_SCADUTO                  => "Pagamento in attesa risulta scaduto alla PAA."
    case PAA_SINTASSI_XSD                       => "Errore di sintassi XSD."
    case PAA_SINTASSI_EXTRAXSD                  => "Errore di sintassi extra XSD."
    case PAA_SEMANTICA                          => "Errore semantico."
    case PAA_ATTIVA_RPT_IMPORTO_NON_VALIDO      => "Importo Singolo Versamento non specificato."
    case CANALE_SINTASSI_XSD                    => "Risposta dal Canale: Errore di sintassi XSD."
    case CANALE_SINTASSI_EXTRAXSD               => "Risposta dal Canale: Errore di sintassi extra XSD."
    case CANALE_SEMANTICA                       => "Risposta dal Canale: Errore semantico."
    case CANALE_RPT_DUPLICATA                   => "Risposta dal Canale: RPT duplicata."
    case CANALE_RPT_SCONOSCIUTA                 => "Risposta dal Canale: RPT sconosciuta."
    case CANALE_RPT_RIFIUTATA                   => "Risposta dal Canale: RPT rifiutata."
    case CANALE_RT_SCONOSCIUTA                  => "Risposta dal Canale: RT sconosciuta."
    case CANALE_RT_NON_DISPONIBILE              => "Risposta dal Canale: RT non ancora pronta."
    case CANALE_INDISPONIBILE                   => "Risposta dal Canale: Servizio non disponibile."
    case CANALE_RICHIEDENTE_ERRATO              => "Risposta dal Canale: Identificativo Richiedente non valido."
    case CANALE_SYSTEM_ERROR                    => "Errore generico."
    case PPT_LOCAL_ERROR_PROTOCOLLO             => "Errore SOAP."
    case PPT_LOCAL_ERROR_EXTRAXSD               => "Errore semantico nella valutazione della response."
    case PPT_LOCAL_ERROR_FSM                    => "Errore nella macchina a stati del pagamento."
    case PPT_RT_SEGNO_DISCORDE                  => "Esito discorde rispetto a redirect."
    case OGGETTO_DISABILITATO                   => "Oggetto disabilitato."
    case PPT_STAZIONE_INT_PA_ERRORE_RESPONSE    => "Errore di risposta dalla stazione."
    case PPT_RT_DUPLICATA                       => "La RT inviata dal PSP è già stata inviata (RT push)."
    case PPT_RT_IN_GESTIONE                     => "La gestione della RT è in corso."
    case CANALE_CONVENZIONE_NON_VALIDA          => "Risposta dal Canale: convenzione non valida"
    case PAA_CONVENZIONE_NON_VALIDA             => "Convenzione non valida"
    case PPT_TOKEN_SCONOSCIUTO                  => "unknown token"
    case PPT_PAGAMENTO_IN_CORSO                 => "Pagamento in attesa risulta in corso al sistema pagoPA"
    case PPT_PAGAMENTO_DUPLICATO                => "Pagamento in attesa risulta concluso al sistema pagoPA"
    case PPT_PAGAMENTO_SCONOSCIUTO              => "Pagamento sconosciuto"
    case PPT_TOKEN_SCADUTO                      => "paymentToken is expired"
    case PPT_TOKEN_SCADUTO_KO                   => "paymentToken is expired on outcome KO"
    case PPT_ERRORE_IDEMPOTENZA                 => "Errore idempotenza"
    case PPT_ESITO_GIA_ACQUISITO                => "L'esito del pagamento risulta già acquisito dal sistema pagoPA."
    case PPT_IBAN_ACCREDITO                     => "Iban accredito non disponibile"
    case PPT_SERVIZIO_NONATTIVO                 => "Servizio non attivo"
    case PPT_SOAPACTION_ERRATA                  => "SOAPAction errata."
    case PPT_SERVIZIO_SCONOSCIUTO               => "Servizio inesistente sul sistema pagoPA"
    case PPT_VERSIONE_SERVIZIO                  => "Versione servizio incompatibile con la chiamata"
    case _                                      => "Errore generico."
  }
  implicit def exception(value: DigitPaErrorCodes.Value): DigitPaException = DigitPaException(value)
}
