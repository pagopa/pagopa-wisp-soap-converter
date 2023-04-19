package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Sender extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Sender] {

    def write(sender: Sender): JsObject = {
      var fields: Map[String, JsValue] = {
        Map(
          "idPsp" -> JsString(sender.idPsp),
          "idBroker" -> JsString(sender.idBroker),
          "idChannel" -> JsString(sender.idChannel),
          "password" -> JsString(sender.password),
          "type" -> JsString(sender._type),
          "id" -> JsString(sender.id)
        )
      }

      if (sender.name.isDefined) {
        fields = fields + ("name" -> JsString(sender.name.get))
      }
      JsObject(fields)
    }

    def read(value: JsValue): Sender = ???
  }

}

case class Sender(
                   idPsp: String,
                   idBroker: String,
                   idChannel: String,
                   password: String,
                   _type: String,
                   name: Option[String],
                   id: String
                 )

