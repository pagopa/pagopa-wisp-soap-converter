package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}

object Receiver extends DefaultJsonProtocol {

  implicit object SenderJsonFormat extends RootJsonFormat[Receiver] {

    def write(receiver: Receiver): JsObject = {
      JsObject(Map(
        "id" -> JsString(receiver.id),
        "organizationId" -> JsString(receiver.organizationId),
        "organizationName" -> JsString(receiver.organizationName)
      ))
    }

    def read(value: JsValue): Receiver = ???
  }

}

case class Receiver(
                     id: String,
                     organizationId: String,
                     organizationName: String
                   )

