package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions
import scala.util.Try

object GetResponse extends DefaultJsonProtocol {

  implicit object GetResponseFormat extends RootJsonFormat[GetResponse] {
    def write(nifr: GetResponse): JsObject = ???

    def read(json: JsValue): GetResponse = {
      val map = json.asInstanceOf[JsObject].fields
      val senderType = map("sender").asInstanceOf[JsObject].fields("type").asInstanceOf[JsString].value
      val senderId = map("sender").asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value
      val senderPspId = map("sender").asInstanceOf[JsObject].fields("pspId").asInstanceOf[JsString].value
      val senderPspName = map("sender").asInstanceOf[JsObject].fields("pspName").asInstanceOf[JsString].value
      val senderBrokerId = map("sender").asInstanceOf[JsObject].fields("pspBrokerId").asInstanceOf[JsString].value
      val senderChannelId = map("sender").asInstanceOf[JsObject].fields("channelId").asInstanceOf[JsString].value
      val senderPassword = map("sender").asInstanceOf[JsObject].fields("password").asInstanceOf[JsString].value

      val receiverId = map("receiver").asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value
      val receiverEcId = map("receiver").asInstanceOf[JsObject].fields("organizationId").asInstanceOf[JsString].value
      val receiverEcName = map("receiver").asInstanceOf[JsObject].fields("organizationName").asInstanceOf[JsString].value

      Try(
        GetResponse(
          map("revision").asInstanceOf[JsNumber].value.toLong,
          map("created").asInstanceOf[JsString].value,
          map("updated").asInstanceOf[JsString].value,
          map("status").asInstanceOf[JsString].value,
          map("fdr").asInstanceOf[JsString].value,
          map("fdrDate").asInstanceOf[JsString].value,
          Sender(SenderTypeEnum.withName(senderType), senderId, senderPspId, senderPspName, senderBrokerId, senderChannelId, senderPassword),
          Receiver(receiverId, receiverEcId, receiverEcName),
          map("regulation").asInstanceOf[JsString].value,
          map("regulationDate").asInstanceOf[JsString].value,
          map("bicCodePouringBank").asInstanceOf[JsString].value,
          map("computedTotPayments").asInstanceOf[JsNumber].value.toLong,
          map("computedSumPayments").asInstanceOf[JsNumber].value
        )
      ).recover({ case _ =>
        throw DeserializationException("GetResponse expected")
      }).get
    }

  }
}

case class GetResponse (
  revision: Long,
  created: String,
  updated: String,
  status: String,
  fdr: String,
  fdrDate: String,
  sender: Sender,
  receiver: Receiver,
  regulation: String,
  regulationDate: String,
  bicCodePouringBank: String,
  computedTotPayments: Long,
  computedSumPayments: BigDecimal)
