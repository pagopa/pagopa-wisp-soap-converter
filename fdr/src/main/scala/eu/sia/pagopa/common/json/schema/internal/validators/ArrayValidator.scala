package eu.sia.pagopa.common.json.schema.internal.validators

import com.osinka.i18n.Lang
import eu.sia.pagopa.common.json.schema.internal.validation.VA
import eu.sia.pagopa.common.json.schema.internal.{ Keywords, Results, SchemaUtil, ValidatorMessages }
import eu.sia.pagopa.common.json.schema.{ SchemaArray, SchemaResolutionContext }
import play.api.libs.json.{ JsArray, JsValue }
import scalaz.{ Failure, Success }

object ArrayValidator extends SchemaTypeValidator[SchemaArray] {

  override def validate(schema: SchemaArray, json: => JsValue, context: SchemaResolutionContext)(implicit lang: Lang): VA[JsValue] = {
    json match {
      case JsArray(values) =>
        val elements: Seq[VA[JsValue]] = values.toSeq.zipWithIndex.map { case (jsValue, idx) =>
          schema.item.validate(jsValue, context.updateScope(_.copy(instancePath = context.instancePath \ idx.toString)))
        }
        if (elements.exists(_.isFailure)) {
          Failure(elements.collect { case Failure(err) => err }.reduceLeft(_ ++ _))
        } else {
          val updatedArr = JsArray(elements.collect { case Success(js) => js })
          schema.constraints.validate(schema, updatedArr, context)
        }
      case _ =>
        Results.failureWithPath(Keywords.Any.Type, ValidatorMessages("err.expected.type", SchemaUtil.typeOfAsString(json)), context, json)
    }
  }

}
