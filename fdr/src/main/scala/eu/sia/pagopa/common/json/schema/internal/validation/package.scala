package eu.sia.pagopa.common.json.schema.internal

import play.api.libs.json.{ JsPath, JsonValidationError }
import scalaz.Validation

package object validation {
  type Validated[E, O] = Validation[Seq[E], O]
  type Mapping[E, I, O] = I => Validated[E, O]
  type Constraint[T] = Mapping[JsonValidationError, T, T]
  type VA[O] = Validated[(JsPath, Seq[JsonValidationError]), O]
}
