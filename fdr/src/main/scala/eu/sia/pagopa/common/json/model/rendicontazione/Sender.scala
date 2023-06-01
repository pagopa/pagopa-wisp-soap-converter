package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Sender extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Sender] {

    def write(sender: Sender): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "type" -> JsString(sender._type.toString),
          "id" -> JsString(sender.id),
          "pspId" -> JsString(sender.pspId),
          "pspName" -> JsString(sender.pspName),
          "brokerId" -> JsString(sender.brokerId),
          "channelId" -> JsString(sender.channelId),
          "password" -> JsString(sender.password)
        )
      }

      JsObject(fields)
    }

    def read(value: JsValue): Sender = ???
  }

}

case class Sender(
                   _type: SenderTypeEnum.Value,
                   id: String,
                   pspId: String,
                   pspName: String,
                   brokerId: String,
                   channelId: String,
                   password: String
                 )

object SenderTypeEnum extends Enumeration {
  val LEGAL_PERSON, ABI_CODE, BIC_CODE = Value
}
