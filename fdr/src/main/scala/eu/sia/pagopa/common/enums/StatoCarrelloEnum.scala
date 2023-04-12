package eu.sia.pagopa.common.enums

//noinspection ScalaStyle
object StatoCarrelloEnum extends Enumeration {
  val CART_RICEVUTO_NODO, CART_ACCETTATO_NODO, CART_RIFIUTATO_NODO, CART_ERRORE_INVIO_A_PSP, CART_INVIATO_A_PSP, CART_ACCETTATO_PSP, CART_RIFIUTATO_PSP, CART_ANNULLATO_WISP,
      CART_ESITO_SCONOSCIUTO_PSP, CART_RT_GENERATA_NODO, CART_PARCHEGGIATO_NODO, CART_RT_ACCETTATA_PA, CART_RT_RIFIUTATA_PA, CART_RT_ESITO_SCONOSCIUTO_PA =
    Value
}
