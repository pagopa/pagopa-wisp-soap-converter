package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions
import scala.util.Try

object NotifyFlowRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[NotifyFlowRequest] = new RootJsonFormat[NotifyFlowRequest] {
    def write(req: NotifyFlowRequest): JsObject = {
      JsObject(Map[String, JsValue](
        "fdr" -> JsString(req.fdr),
        "pspId" -> JsString(req.pspId),
        "retry" -> JsNumber(req.retry),
        "revision" -> JsNumber(req.revision)
      ))
    }

    def read(json: JsValue): NotifyFlowRequest = {
      val map = json.asJsObject.fields
      Try(
        NotifyFlowRequest(
          map("fdr").asInstanceOf[JsString].value,
          map("pspId").asInstanceOf[JsString].value,
          map("retry").asInstanceOf[JsNumber].value.toInt,
          map("revision").asInstanceOf[JsNumber].value.toInt
        )
      ).recover({ case _ =>
                throw DeserializationException("NotifyFlowRequest expected")
              }).get
    }

  }
}

case class NotifyFlowRequest(fdr: String, pspId: String, retry: Integer, revision: Integer)

object NotifyFlowResponse {}

case class NotifyFlowResponse()