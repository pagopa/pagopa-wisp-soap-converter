package eu.sia.pagopa.common.json.model.rendicontazione

import scalaxb.Base64Binary
import spray.json.{JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions

object GetAllRevisionFdrResponse {
  implicit val format: RootJsonFormat[GetAllRevisionFdrResponse] = new RootJsonFormat[GetAllRevisionFdrResponse] {
    def write(res: GetAllRevisionFdrResponse): JsObject = {
      JsObject(Map[String, JsValue](
        "xmlRendicontazione" -> JsString(res.xmlRendicontazione.toString)
      ))
    }

    def read(json: JsValue): GetAllRevisionFdrResponse = ???

  }
}

case class GetAllRevisionFdrResponse(xmlRendicontazione: Base64Binary)