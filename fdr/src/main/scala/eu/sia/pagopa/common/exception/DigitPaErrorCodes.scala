package eu.sia.pagopa.common.exception

import scala.language.implicitConversions

object DigitPaErrorCodes extends Enumeration {
  val PPT_SYSTEM_ERROR,
      PPT_AUTORIZZAZIONE,
      PPT_AUTENTICAZIONE,
      PPT_SINTASSI_XSD,
      PPT_SINTASSI_EXTRAXSD,
      PPT_SEMANTICA,
      PPT_CODIFICA_PSP_SCONOSCIUTA,
      PPT_PSP_SCONOSCIUTO,
      PPT_PSP_DISABILITATO,
      PPT_TIPO_VERSAMENTO_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PSP_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PSP_DISABILITATO,
      PPT_CANALE_SCONOSCIUTO,
      PPT_CANALE_DISABILITATO,
      PPT_DOMINIO_SCONOSCIUTO,
      PPT_DOMINIO_DISABILITATO,
      PPT_INTERMEDIARIO_PA_SCONOSCIUTO,
      PPT_INTERMEDIARIO_PA_DISABILITATO,
      PPT_STAZIONE_INT_PA_SCONOSCIUTA,
      PPT_STAZIONE_INT_PA_DISABILITATA,
      PPT_ID_FLUSSO_SCONOSCIUTO,
      PPT_SOAPACTION_ERRATA = Value

  def description(value: DigitPaErrorCodes.Value): String = value match {
    case PPT_SYSTEM_ERROR                       => "Errore generico."
    case PPT_AUTORIZZAZIONE                     => "Il richiedente non ha i diritti per l'operazione."
    case PPT_AUTENTICAZIONE                     => "Errore di autenticazione."
    case PPT_SINTASSI_XSD                       => "Errore di sintassi XSD."
    case PPT_SINTASSI_EXTRAXSD                  => "Errore di sintassi extra XSD."
    case PPT_SEMANTICA                          => "Errore semantico."
    case PPT_CODIFICA_PSP_SCONOSCIUTA           => "Valore di codificaInfrastruttura PSP non censito."
    case PPT_PSP_SCONOSCIUTO                    => "PSP sconosciuto."
    case PPT_PSP_DISABILITATO                   => "PSP conosciuto ma disabilitato da configurazione."
    case PPT_TIPO_VERSAMENTO_SCONOSCIUTO        => "Identificativo tipo versamento sconosciuto."
    case PPT_INTERMEDIARIO_PSP_SCONOSCIUTO      => "Identificativo intermediario psp sconosciuto."
    case PPT_INTERMEDIARIO_PSP_DISABILITATO     => "Intermediario psp disabilitato."
    case PPT_CANALE_SCONOSCIUTO                 => "Canale sconosciuto."
    case PPT_CANALE_DISABILITATO                => "Canale conosciuto ma disabilitato da configurazione."
    case PPT_DOMINIO_SCONOSCIUTO                => "Identificativo Dominio sconosciuto."
    case PPT_DOMINIO_DISABILITATO               => "Dominio disabilitato."
    case PPT_INTERMEDIARIO_PA_SCONOSCIUTO       => "Identificativo intermediario dominio sconosciuto."
    case PPT_INTERMEDIARIO_PA_DISABILITATO      => "Intermediario dominio disabilitato."
    case PPT_STAZIONE_INT_PA_SCONOSCIUTA        => "IdentificativoStazioneRichiedente sconosciuto."
    case PPT_STAZIONE_INT_PA_DISABILITATA       => "Stazione disabilitata."
    case PPT_ID_FLUSSO_SCONOSCIUTO              => "Identificativo flusso sconosciuto."
    case PPT_SOAPACTION_ERRATA                  => "SOAPAction errata."
    case _                                      => "Errore generico."
  }
  implicit def exception(value: DigitPaErrorCodes.Value): DigitPaException = DigitPaException(value)
}
