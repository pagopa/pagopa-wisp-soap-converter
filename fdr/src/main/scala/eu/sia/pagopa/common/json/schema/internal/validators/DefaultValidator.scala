package eu.sia.pagopa.common.json.schema.internal.validators

import com.osinka.i18n.Lang
import eu.sia.pagopa.common.json.schema.internal.validation.VA
import eu.sia.pagopa.common.json.schema.{ SchemaResolutionContext, SchemaType }
import play.api.libs.json.JsValue

class DefaultValidator[A <: SchemaType] extends SchemaTypeValidator[A] {
  override def validate(schema: A, json: => JsValue, context: SchemaResolutionContext)(implicit lang: Lang): VA[JsValue] = {
    schema.constraints.validate(schema, json, context)
  }
}
