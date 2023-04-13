package eu.sia.pagopa.common.json.model

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object Error extends DefaultJsonProtocol {
  implicit val errorFormat: RootJsonFormat[Error] = DefaultJsonProtocol.jsonFormat1(Error.apply)
}

case class Error(error: String)
