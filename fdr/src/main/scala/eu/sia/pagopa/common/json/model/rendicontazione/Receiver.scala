package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Receiver extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Receiver] {

    def write(receiver: Receiver): JsObject = {
      JsObject(Map(
        "id" -> JsString(receiver.id),
        "ecId" -> JsString(receiver.ecId),
        "ecName" -> JsString(receiver.ecName)
      ))
    }

    def read(value: JsValue): Receiver = ???
  }

}

case class Receiver(
                     id: String,
                     ecId: String,
                     ecName: String
                   )

