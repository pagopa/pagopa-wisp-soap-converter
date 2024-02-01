package eu.sia.pagopa.common.json.model.rendicontazione

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.net.URI
import scala.language.implicitConversions

object GenericResponse extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object GenericResponseFormat extends JsonFormat[String] {
    def write(enum: String) = JsString(enum)
    def read(json: JsValue) = json match {
      case JsString(esito) => esito
      case other           => deserializationError("Expected GenericResponseOutcome, got: " + other)
    }
  }

  implicit object URIFormat extends JsonFormat[URI] {
    def write(obj: URI) = JsString(obj.toString)
    def read(json: JsValue) = json match {
      case JsString(uri) => new URI(uri)
      case other         => deserializationError("Expected URI, got: " + other)
    }
  }
  implicit val jsonFormat: RootJsonFormat[GenericResponse] = jsonFormat1(GenericResponse.apply)
}
case class GenericResponse(message: String)

object GenericResponseOutcome extends Enumeration {
  val OK, KO = Value
}
