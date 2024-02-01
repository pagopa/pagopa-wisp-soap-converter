package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions

object GetXmlRendicontazioneResponse {
  implicit val format: RootJsonFormat[GetXmlRendicontazioneResponse] = new RootJsonFormat[GetXmlRendicontazioneResponse] {
    def write(res: GetXmlRendicontazioneResponse): JsObject = {
      JsObject(Map[String, JsValue](
        "xmlRendicontazione" -> JsString(res.xmlRendicontazione)
      ))
    }

    def read(json: JsValue): GetXmlRendicontazioneResponse = ???

  }
}

case class GetXmlRendicontazioneResponse(xmlRendicontazione: String)