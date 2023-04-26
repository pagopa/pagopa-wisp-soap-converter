package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Receiver extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Receiver] {

    def write(receiver: Receiver): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "ecId" -> JsString(receiver.ecId),
          "id" -> JsString(receiver.id)
        )
      }
      if (receiver.ecName.isDefined) {
        fields = fields + ("ecName" -> JsString(receiver.ecName.get))
      }
      JsObject(fields)
    }

    def read(value: JsValue): Receiver = ???
  }

}

case class Receiver(
                     id: String,
                     ecId: String,
                     ecName: Option[String])

