package eu.sia.pagopa.common.json.model

import spray.json.{ DeserializationException, JsBoolean, JsObject, JsString, JsValue, RootJsonFormat }

object ActionResponse {

  implicit val simpleSuccessFormat: RootJsonFormat[ActionResponse] = new RootJsonFormat[ActionResponse]() {
    def write(ar: ActionResponse): JsObject = {
      val fields: Map[String, JsValue] = Map("success" -> JsBoolean(ar.success), "action" -> JsString(ar.action), "description" -> JsString(ar.description))
      JsObject(fields)
    }

    def read(json: JsValue): ActionResponse = {
      json.asJsObject.getFields("success", "action", "description") match {
        case Seq(JsBoolean(s1), JsString(s2), JsString(s3)) =>
          ActionResponse(s1, s2, s3)
        case _ => throw DeserializationException("ActionResponse expected")
      }
    }
  }
}
case class ActionResponse(success: Boolean, action: String, description: String)
