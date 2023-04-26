package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Sender extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Sender] {

    def write(sender: Sender): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "pspId" -> JsString(sender.pspId),
          "brokerId" -> JsString(sender.brokerId),
          "channelId" -> JsString(sender.channelId),
          "password" -> JsString(sender.password),
          "type" -> JsString(sender._type.toString),
          "id" -> JsString(sender.id)
        )
      }

      if (sender.pspName.isDefined) {
        fields = fields + ("pspName" -> JsString(sender.pspName.get))
      }
      JsObject(fields)
    }

    def read(value: JsValue): Sender = ???
  }

}

case class Sender(
                   pspId: String,
                   brokerId: String,
                   channelId: String,
                   password: String,
                   _type: SenderTypeEnum.Value,
                   pspName: Option[String],
                   id: String
                 )

object SenderTypeEnum extends Enumeration {
  val LEGAL_PERSON, ABI_CODE, BIC_CODE = Value
}
