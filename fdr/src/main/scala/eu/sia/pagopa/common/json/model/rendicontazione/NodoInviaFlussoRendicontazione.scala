package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions

object NodoInviaFlussoRendicontazioneRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[NodoInviaFlussoRendicontazioneRequest] = new RootJsonFormat[NodoInviaFlussoRendicontazioneRequest] {
    def write(nifr: NodoInviaFlussoRendicontazioneRequest): JsObject = {
      var fields: Map[String, JsValue] =
        Map(
          "reportingFlow" -> JsString(nifr.reportingFlow),
          "dateReportingFlow" -> JsString(nifr.dateReportingFlow),
          "sender" -> nifr.sender.toJson,
          "receiver" -> nifr.receiver.toJson,
          "regulation" -> JsString(nifr.regulation),
          "dateRegulation" -> JsString(nifr.dateRegulation),
          "payments" -> JsArray(nifr.payments.map(_.toJson).toList)
        )

      if (nifr.bicCodePouringBank.isDefined) {
        fields = fields + ("bicCodePouringBank" -> JsString(nifr.bicCodePouringBank.get))
      }

      JsObject(fields)
    }

    def read(json: JsValue): NodoInviaFlussoRendicontazioneRequest = ???

  }
}

case class NodoInviaFlussoRendicontazioneRequest(
                                                  reportingFlow: String,
                                                  dateReportingFlow: String,
                                                  sender: Sender,
                                                  receiver: Receiver,
                                                  regulation: String,
                                                  dateRegulation: String,
                                                  bicCodePouringBank: Option[String],
                                                  payments: Seq[Payment]
                                                )

object NodoInviaFlussoRendicontazioneResponse {}

case class NodoInviaFlussoRendicontazioneResponse()