package eu.sia.pagopa.common.json.model.rendicontazione

import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, enrichAny}

import scala.language.implicitConversions

object InviaFlussoRendicontazioneRequest extends DefaultJsonProtocol {

  implicit val format: RootJsonFormat[InviaFlussoRendicontazioneRequest] = new RootJsonFormat[InviaFlussoRendicontazioneRequest] {
    def write(nifr: InviaFlussoRendicontazioneRequest): JsObject = {
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

    def read(json: JsValue): InviaFlussoRendicontazioneRequest = ???

//    def read(json: JsValue): InviaFlussoRendicontazioneRequest = {
//      val map = json.asJsObject.fields
//      val transationId = map.get("additionalPaymentInformations").map(_.asInstanceOf[JsObject].fields("transactionId").asInstanceOf[JsString].value)
//      val outcomePaymentGateway =
//        map.get("additionalPaymentInformations").map(_.asInstanceOf[JsObject].fields("outcomePaymentGateway").asInstanceOf[JsString].value)
//      val authorizationCode = map.get("additionalPaymentInformations").map(_.asInstanceOf[JsObject].fields("authorizationCode").asInstanceOf[JsString].value)
//      val additionalPaymentInformations = if (transationId.isDefined) {
//        Some(AdditionalPaymentInformations(transationId.get, outcomePaymentGateway.get, authorizationCode.get))
//      } else {
//        None
//      }
//
//      Try(
//        NodoInviaFlussoRendicontazioneRequest(
//          map("paymentTokens").asInstanceOf[JsArray].elements.map(s => s.asInstanceOf[JsString].value),
//          map("outcome").asInstanceOf[JsString].value,
//          map.get("identificativoPsp").map(_.asInstanceOf[JsString].value),
//          map.get("tipoVersamento").map(_.asInstanceOf[JsString].value),
//          map.get("identificativoIntermediario").map(_.asInstanceOf[JsString].value),
//          map.get("identificativoCanale").map(_.asInstanceOf[JsString].value),
//          map.get("pspTransactionId").map(_.asInstanceOf[JsString].value),
//          map.get("totalAmount").map(_.asInstanceOf[JsNumber].value),
//          map.get("fee").map(_.asInstanceOf[JsNumber].value),
//          map.get("timestampOperation").map(_.asInstanceOf[JsString].value),
//          additionalPaymentInformations
//        )
//      ).recover({ case _ =>
//        throw DeserializationException("ClosePayment expected")
//      }).get
//    }

  }
}

case class InviaFlussoRendicontazioneRequest(
                                                  reportingFlow: String,
                                                  dateReportingFlow: String,
                                                  sender: Sender,
                                                  receiver: Receiver,
                                                  regulation: String,
                                                  dateRegulation: String,
                                                  bicCodePouringBank: Option[String],
                                                  payments: Seq[Payment]
                                                )

object InviaFlussoRendicontazioneResponse {}

case class InviaFlussoRendicontazioneResponse()