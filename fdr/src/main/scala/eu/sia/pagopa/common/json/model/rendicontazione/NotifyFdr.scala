package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.language.implicitConversions
import scala.util.Try

object NotifyFdrRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[NotifyFdrRequest] = new RootJsonFormat[NotifyFdrRequest] {
    def write(req: NotifyFdrRequest): JsObject = {
      JsObject(Map[String, JsValue](
        "fdr" -> JsString(req.fdr),
        "pspId" -> JsString(req.pspId),
        "organizationId" -> JsString(req.organizationId),
        "retry" -> JsNumber(req.retry),
        "revision" -> JsNumber(req.revision)
      ))
    }

    def read(json: JsValue): NotifyFdrRequest = {
      val map = json.asJsObject.fields
      Try(
        NotifyFdrRequest(
          map("fdr").asInstanceOf[JsString].value,
          map("pspId").asInstanceOf[JsString].value,
          map("organizationId").asInstanceOf[JsString].value,
          map("retry").asInstanceOf[JsNumber].value.toInt,
          map("revision").asInstanceOf[JsNumber].value.toInt
        )
      ).recover({ case _ =>
                throw DeserializationException("NotifyFlowRequest expected")
              }).get
    }

  }
}

case class NotifyFdrRequest(fdr: String, pspId: String, organizationId: String, retry: Integer, revision: Integer)

//object NotifyFdrResponse extends DefaultJsonProtocol {
//
//  implicit val format: RootJsonFormat[NotifyFdrResponse] = new RootJsonFormat[NotifyFdrResponse] {
//    def write(res: NotifyFdrResponse): JsObject = {
//      var fields: Map[String, JsValue] =
//        Map("outcome" -> JsString(res.outcome))
//      if (res.description.isDefined) {
//        fields = fields + ("description" -> JsString(res.description.get))
//      }
//      JsObject(fields)
//    }
//
//    def read(json: JsValue): NotifyFdrResponse = ???
//  }
//}
//
//case class NotifyFdrResponse(outcome: String, description: Option[String])