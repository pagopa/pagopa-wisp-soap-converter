package eu.sia.pagopa.common.json.model.rendicontazione

import eu.sia.pagopa.common.util.Util
import eu.sia.pagopa.common.util.xml.XmlUtil
import scalaxb.Base64Binary
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}

import java.util.Base64
import scala.language.implicitConversions
import scala.util.Try

object GetXmlRendicontazioneResponse {
  implicit val format: RootJsonFormat[GetXmlRendicontazioneResponse] = new RootJsonFormat[GetXmlRendicontazioneResponse] {
    def write(res: GetXmlRendicontazioneResponse): JsObject = {
      JsObject(Map[String, JsValue](
        "xmlRendicontazione" -> JsString(res.xmlRendicontazione.toString)
      ))
    }

    def read(json: JsValue): GetXmlRendicontazioneResponse = {
      val map = json.asJsObject.fields
      val xmlRendicontazione = map("xmlRendicontazione").asInstanceOf[JsString].value
      val xmlUnzipped = Util.unzipContent(Base64.getDecoder().decode(xmlRendicontazione))
      Try(GetXmlRendicontazioneResponse(XmlUtil.StringBase64Binary.encodeBase64(new String(xmlUnzipped.get))))
        .recover({ case _ =>
          throw DeserializationException("GetXmlRendicontazioneResponse expected")
        })
        .get
    }


  }
}

case class GetXmlRendicontazioneResponse(xmlRendicontazione: Base64Binary)