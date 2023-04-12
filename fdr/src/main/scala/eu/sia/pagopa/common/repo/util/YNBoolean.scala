package eu.sia.pagopa.common.repo.util

import scala.language.implicitConversions

object YNBoolean extends Enumeration {
  type YNBoolean = Value
  val True, False = Value

  implicit def value2Boolean(v: YNBoolean): Boolean = v match {
    case True  => true
    case False => false
  }

  implicit def boolean2Value(v: Boolean): YNBoolean = v match {
    case true => YNBoolean.True
    case _    => YNBoolean.False
  }
}
