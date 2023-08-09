package eu.sia.pagopa.common.json.schema.internal.validators

import com.osinka.i18n.Lang
import eu.sia.pagopa.common.json.schema.internal.validation.VA
import eu.sia.pagopa.common.json.schema.internal.{ Keywords, Results, ValidatorMessages }
import eu.sia.pagopa.common.json.schema.{ CompoundSchemaType, _ }
import play.api.libs.json.JsValue

object CompoundValidator extends SchemaTypeValidator[CompoundSchemaType] {
  override def validate(schema: CompoundSchemaType, json: => JsValue, context: SchemaResolutionContext)(implicit lang: Lang): VA[JsValue] = {
    val result: Option[VA[JsValue]] = schema.alternatives.map(_.validate(json, context)).find(_.isSuccess)

    result.getOrElse(Results.failureWithPath(Keywords.Any.Type, ValidatorMessages("comp.no.schema"), context, json))
  }
}
