package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions

object InviaFlussoRendicontazioneRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[InviaFlussoRendicontazioneRequest] = new RootJsonFormat[InviaFlussoRendicontazioneRequest] {
    def write(nifr: InviaFlussoRendicontazioneRequest): JsObject = {
      var fields: Map[String, JsValue] =
        Map(
          "reportingFlowName" -> JsString(nifr.reportingFlowName),
          "dateReportingFlow" -> JsString(nifr.reportingFlowDate),
          "sender" -> nifr.sender.toJson,
          "receiver" -> nifr.receiver.toJson,
          "regulation" -> JsString(nifr.regulation),
          "dateRegulation" -> JsString(nifr.dateRegulation),
          "payments" -> JsArray(nifr.payments.map(_.toJson).toList)
        )

      if (nifr.bicCodePouringBank.isDefined) {
        fields = fields + ("bicCodePouringBank" -> JsString(nifr.bicCodePouringBank.get))
      }
      if (nifr.bicCodePouringBank.isDefined) {
        fields = fields + ("bicCodePouringBank" -> JsString(nifr.bicCodePouringBank.get))
      }
      if (nifr.bicCodePouringBank.isDefined) {
        fields = fields + ("bicCodePouringBank" -> JsString(nifr.bicCodePouringBank.get))
      }

      if (nifr.bicCodePouringBank.isDefined) {
        fields = fields + ("bicCodePouringBank" -> JsString(nifr.bicCodePouringBank.get))
      }
      JsObject(fields)
    }

    def read(json: JsValue): InviaFlussoRendicontazioneRequest = ???

//    def read(json: JsValue): InviaFlussoRendicontazioneRequest = {
//      val map = json.asJsObject.fields
//      val senderType = map.get("sender").map(_.asInstanceOf[JsObject].fields("type").asInstanceOf[JsString].value)
//      val senderId = map.get("sender").map(_.asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value)
//      val senderPspId = map.get("sender").map(_.asInstanceOf[JsObject].fields("pspId").asInstanceOf[JsString].value)
//      val senderPspName = map.get("sender").map(_.asInstanceOf[JsObject].fields("pspName").asInstanceOf[JsString].value)
//      val senderBrokerId = map.get("sender").map(_.asInstanceOf[JsObject].fields("brokerId").asInstanceOf[JsString].value)
//      val senderChannelId = map.get("sender").map(_.asInstanceOf[JsObject].fields("channelId").asInstanceOf[JsString].value)
//      val senderPassword = map.get("sender").map(_.asInstanceOf[JsObject].fields("password").asInstanceOf[JsString].value)
//
//      val receiverId = map.get("receiver").map(_.asInstanceOf[JsObject].fields("id").asInstanceOf[JsString].value)
//      val receiverEcId = map.get("receiver").map(_.asInstanceOf[JsObject].fields("ecId").asInstanceOf[JsString].value)
//      val receiverEcName = map.get("receiver").map(_.asInstanceOf[JsObject].fields("ecName").asInstanceOf[JsString].value)
//
//      Try(
//        InviaFlussoRendicontazioneRequest(
//          map("reportingFlowName").asInstanceOf[JsString].value,
//          map("reportingFlowDate").asInstanceOf[JsString].value,
//          Sender(senderType, senderId, senderPspId, senderPspName, senderBrokerId, senderChannelId, senderPassword),
//          Receiver(receiverId, receiverEcId, receiverEcName),
//          map.get("regulation").map(_.asInstanceOf[JsString].value),
//          map.get("regulationDate").map(_.asInstanceOf[JsString].value),
//          map.get("bicCodePouringBank").map(_.asInstanceOf[JsString].value),
//          Nil
//        )
//      ).recover({ case _ =>
//        throw DeserializationException("ClosePayment expected")
//      }).get
//    }

  }
}

case class InviaFlussoRendicontazioneRequest(
                                              reportingFlowName: String,
                                              reportingFlowDate: String,
                                              sender: Sender,
                                              receiver: Receiver,
                                              regulation: String,
                                              dateRegulation: String,
                                              bicCodePouringBank: Option[String],
                                              payments: Seq[Payment]
                                            )

object InviaFlussoRendicontazioneResponse {}

case class InviaFlussoRendicontazioneResponse()