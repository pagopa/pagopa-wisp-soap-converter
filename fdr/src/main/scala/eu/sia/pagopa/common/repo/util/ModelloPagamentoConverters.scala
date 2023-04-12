package eu.sia.pagopa.common.repo.util

object ModelloPagamentoConverters {

  private val map = Map(ModelloPagamento.IMMEDIATO -> "0", ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO -> "1", ModelloPagamento.DIFFERITO -> "2", ModelloPagamento.ATTIVATO_PRESSO_PSP -> "4")

  final def fromModelloPagamento(modelloPagamento: ModelloPagamento.Value): (ModelloPagamento.Value, String) = {
    map.find(_._1 == modelloPagamento).getOrElse(throw new NoSuchElementException(s"No value found for '$modelloPagamento'"))
  }

  final def fromXmlValue(xmlValue: String): (ModelloPagamento.Value, String) = {
    map.find(_._2 == xmlValue).getOrElse(throw new NoSuchElementException(s"No value found for '$xmlValue'"))
  }

}
