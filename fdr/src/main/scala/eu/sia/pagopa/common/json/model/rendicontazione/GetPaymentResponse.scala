package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions
import scala.util.Try

object GetPaymentResponse extends DefaultJsonProtocol {


  implicit object PaymentsJsonFormat extends RootJsonFormat[GetPaymentResponse] {
    def write(payments: GetPaymentResponse): JsObject = ???

    def read(json: JsValue): GetPaymentResponse = {
      val map = json.asJsObject.fields

      val metadataPageSize = map("metadata").asInstanceOf[JsObject].fields("pageSize").asInstanceOf[JsNumber].value
      val metadataPageNumber = map("metadata").asInstanceOf[JsObject].fields("pageNumber").asInstanceOf[JsNumber].value
      val metadataTotPage = map("metadata").asInstanceOf[JsObject].fields("totPage").asInstanceOf[JsNumber].value

      val paymentsElements = map("data").asInstanceOf[JsArray].elements
      val payments = paymentsElements.map(v =>
        Payment(
          v.asInstanceOf[JsObject].fields("iuv").asInstanceOf[JsString].value,
          v.asInstanceOf[JsObject].fields("iur").asInstanceOf[JsString].value,
          v.asInstanceOf[JsObject].fields("index").asInstanceOf[JsNumber].value.intValue,
          v.asInstanceOf[JsObject].fields("pay").asInstanceOf[JsNumber].value,
          PayStatusEnum.withName(v.asInstanceOf[JsObject].fields("payStatus").asInstanceOf[JsString].value),
          v.asInstanceOf[JsObject].fields("payDate").asInstanceOf[JsString].value
        )
      )

      Try(
        GetPaymentResponse(
          Metadata(metadataPageSize.intValue, metadataPageNumber.intValue, metadataTotPage.intValue),
          map("count").asInstanceOf[JsNumber].value.intValue,
          payments
        )
      ).recover({ case _ =>
        throw DeserializationException("Payment expected")
      }).get
    }

  }

}

case class GetPaymentResponse(
                             metadata: Metadata,
                             count: Long,
                             data: Seq[Payment]
)

case class Metadata (
                      pagesize: Integer,
                      pageNumber: Integer,
                      totPage: Integer
                    )

case class Payment(
                    iuv: String,
                    iur: String,
                    index: Integer,
                    pay: BigDecimal,
                    payStatus: PayStatusEnum.Value,
                    payDate: String
)

object PayStatusEnum extends Enumeration {
  val EXECUTED, REVOKED, NO_RPT = Value
}
