package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions
import scala.util.Try

object Payments extends DefaultJsonProtocol {

  implicit object PaymentsJsonFormat extends RootJsonFormat[Payment] {
    def write(payments: Payment): JsObject = {
      JsObject(Map(
        "iuv" -> JsString(payments.iuv),
        "iur" -> JsString(payments.iur),
        "index" -> JsNumber(payments.index),
        "pay" -> JsNumber(payments.pay),
        "payStatus" -> JsString(payments.payStatus.toString),
        "payDate" -> JsString(payments.payDate)
      ))
    }

    def read(json: JsValue): Payment = {
      val map = json.asJsObject.fields
      Try(
        Payment(
          map("iuv").asInstanceOf[JsString].value,
          map("iur").asInstanceOf[JsString].value,
          map("index").asInstanceOf[JsNumber].value.intValue,
          map("pay").asInstanceOf[JsNumber].value,
          PayStatusEnum.withName(map("payStatus").asInstanceOf[JsString].value),
          map("payDate").asInstanceOf[JsString].value
        )
      ).recover({ case _ =>
        throw DeserializationException("Payment expected")
      }).get
    }
  }

}

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

case class PaymentsRequest(payments: Seq[Payment])

object PaymentsResponse {}

case class PaymentsResponse()


