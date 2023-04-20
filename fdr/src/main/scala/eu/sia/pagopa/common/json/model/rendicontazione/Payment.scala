package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

object Payment extends DefaultJsonProtocol {

  implicit object PaymentsJsonFormat extends RootJsonFormat[Payment] {
    def write(payments: Payment): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "identificativoUnivocoVersamento" -> JsString(payments.identificativoUnivocoVersamento),
          "identificativoUnivocoRiscossione" -> JsString(payments.identificativoUnivocoRiscossione),
          "singoloImportoPagato" -> JsNumber(payments.singoloImportoPagato),
          "codiceEsitoSingoloPagamento" -> JsString(payments.codiceEsitoSingoloPagamento),
          "dataEsitoSingoloPagamento" -> JsString(payments.dataEsitoSingoloPagamento)
        )
      }

      if (payments.indiceDatiSingoloPagamento.isDefined) {
        fields = fields + ("indiceDatiSingoloPagamento" -> JsNumber(payments.indiceDatiSingoloPagamento.get))
      }
      JsObject(fields)
    }

    def read(value: JsValue): Payment = ???
  }

}

case class Payment(
                    identificativoUnivocoVersamento: String,
                    identificativoUnivocoRiscossione: String,
                    indiceDatiSingoloPagamento: Option[Integer],
                    singoloImportoPagato: BigDecimal,
                    codiceEsitoSingoloPagamento: CodiceEsitoSingoloPagamentoEnum.Value,
                    dataEsitoSingoloPagamento: String
                  )

object CodiceEsitoSingoloPagamentoEnum extends Enumeration {
  val PAGAMENTO_ESEGUITO, PAGAMENTO_REVOCATO, PAGAMENTO_NO_RPT = Value
}

