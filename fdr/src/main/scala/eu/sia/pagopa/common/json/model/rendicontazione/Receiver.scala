package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Receiver extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Receiver] {

    def write(receiver: Receiver): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "idEc" -> JsString(receiver.idEc),
          "id" -> JsString(receiver.id)
        )
      }
      if (receiver.nameEc.isDefined) {
        fields = fields + ("nameEc" -> JsString(receiver.nameEc.get))
      }
      JsObject(fields)
    }

    def read(value: JsValue): Receiver = ???
  }

}

case class Receiver(
                   idEc: String,
                   id: String,
                   nameEc: Option[String])

