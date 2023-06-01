package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions
import scala.util.Try

object NotifyFlowRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[NotifyFlowRequest] = new RootJsonFormat[NotifyFlowRequest] {
    def write(req: NotifyFlowRequest): JsObject = {
      JsObject(Map[String, JsValue](
        "reportingFlowName" -> JsString(req.reportingFlowName),
        "reportingFlowDate" -> JsString(req.reportingFlowDate)
      ))
    }

    def read(json: JsValue): NotifyFlowRequest = {
      val map = json.asJsObject.fields
      Try(
        NotifyFlowRequest(
          map("reportingFlowName").asInstanceOf[JsString].value,
          map("reportingFlowDate").asInstanceOf[JsString].value,
        )
      ).recover({ case _ =>
                throw DeserializationException("NotifyFlowRequest expected")
              }).get
    }

  }
}

case class NotifyFlowRequest(reportingFlowName: String, reportingFlowDate: String)

object NotifyFlowResponse {}

case class NotifyFlowResponse()