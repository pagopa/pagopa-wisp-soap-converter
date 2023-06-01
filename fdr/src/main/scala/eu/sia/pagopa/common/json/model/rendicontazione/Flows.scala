package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions
import scala.util.Try

object Flow extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[Flow] = new RootJsonFormat[Flow] {
    def write(nifr: Flow): JsObject = {
      JsObject(Map(
        "reportingFlowName" -> JsString(nifr.reportingFlowName),
        "reportingFlowDate" -> JsString(nifr.reportingFlowDate),
        "sender" -> nifr.sender.toJson,
        "receiver" -> nifr.receiver.toJson,
        "regulation" -> JsString(nifr.regulation),
        "regulationDate" -> JsString(nifr.regulationDate),
        "bicCodePouringBank" -> JsString(nifr.bicCodePouringBank)
      ))
    }

    def read(json: JsValue): Flow = {
      val map = json.asJsObject.fields
      val senderType = map("sender").asInstanceOf[JsObject].fields("type").asInstanceOf[JsString].value
      val senderId = map("sender").asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value
      val senderPspId = map("sender").asInstanceOf[JsObject].fields("pspId").asInstanceOf[JsString].value
      val senderPspName = map("sender").asInstanceOf[JsObject].fields("pspName").asInstanceOf[JsString].value
      val senderBrokerId = map("sender").asInstanceOf[JsObject].fields("brokerId").asInstanceOf[JsString].value
      val senderChannelId = map("sender").asInstanceOf[JsObject].fields("channelId").asInstanceOf[JsString].value
      val senderPassword = map("sender").asInstanceOf[JsObject].fields("password").asInstanceOf[JsString].value

      val receiverId = map("receiver").asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value
      val receiverEcId = map("receiver").asInstanceOf[JsObject].fields("ecId").asInstanceOf[JsString].value
      val receiverEcName = map("receiver").asInstanceOf[JsObject].fields("ecName").asInstanceOf[JsString].value

      Try(
        Flow(
          map("reportingFlowName").asInstanceOf[JsString].value,
          map("reportingFlowDate").asInstanceOf[JsString].value,
          Sender(SenderTypeEnum.withName(senderType), senderId, senderPspId, senderPspName, senderBrokerId, senderChannelId, senderPassword),
          Receiver(receiverId, receiverEcId, receiverEcName),
          map("regulation").asInstanceOf[JsString].value,
          map("regulationDate").asInstanceOf[JsString].value,
          map("bicCodePouringBank").asInstanceOf[JsString].value
        )
      ).recover({ case _ =>
        throw DeserializationException("Flow expected")
      }).get
    }

  }
}

case class Flow(
  reportingFlowName: String,
  reportingFlowDate: String,
  sender: Sender,
  receiver: Receiver,
  regulation: String,
  regulationDate: String,
  bicCodePouringBank: String
)

object FlowsResponse {}

case class FlowsResponse()