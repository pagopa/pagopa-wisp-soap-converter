package eu.sia.pagopa.common.exception

import play.api.libs.json.JsObject

case class JsonException(message: String, field: String, jsonObject: Option[JsObject] = None) extends Exception(message)
