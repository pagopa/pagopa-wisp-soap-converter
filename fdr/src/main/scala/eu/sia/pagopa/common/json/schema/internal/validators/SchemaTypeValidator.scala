package eu.sia.pagopa.common.json.schema.internal.validators

import com.osinka.i18n.Lang
import eu.sia.pagopa.common.json.schema.SchemaResolutionContext
import eu.sia.pagopa.common.json.schema.internal.validation.VA
import play.api.libs.json.JsValue

trait SchemaTypeValidator[S] {
  def validate(schema: S, json: => JsValue, resolutionContext: SchemaResolutionContext)(implicit lang: Lang = Lang.Default): VA[JsValue]
}
