package it.gov.pagopa.common.enums

// format: off
object StatoRPTEnum extends Enumeration {
  val READY, RPT_RISOLTA_OK, RPT_RISOLTA_KO ,RPT_RICEVUTA_NODO, RPT_ACCETTATA_NODO, RPT_RIFIUTATA_NODO, RPT_ERRORE_INVIO_A_PSP,
  RPT_INVIATA_A_PSP, RPT_ACCETTATA_PSP, RPT_RIFIUTATA_PSP, RT_GENERATA_NODO,
  RT_RICEVUTA_NODO, RT_RIFIUTATA_NODO, RT_ACCETTATA_NODO, RT_ACCETTATA_PA, RT_RIFIUTATA_PA,
  RT_ESITO_SCONOSCIUTO_PA, RT_INVIATA_PA, VERIFIED, RECEIVED,
  RPT_ESITO_SCONOSCIUTO_PSP, RPT_PARCHEGGIATA_NODO, RPT_PARCHEGGIATA_NODO_MOD3, RT_ERRORE_INVIO_A_PA = Value
}
