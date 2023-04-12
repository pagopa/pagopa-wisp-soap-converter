package eu.sia.pagopa.common.repo

import scala.language.implicitConversions

final case class BigDecimal2 private (amount: BigDecimal) {
  def plus(y: BigDecimal2): BigDecimal2 = {
    new BigDecimal2(this.amount.+(y.amount))
  }
}

object BigDecimal2 {
  def apply(db: BigDecimal): BigDecimal2 = {
    new BigDecimal2(db.setScale(2))
  }
  implicit def bigDecimalToBigDecimal2(db: BigDecimal): BigDecimal2 = {
    BigDecimal2(db)
  }
  implicit def bigDecimal2ToBigDecimal(am: BigDecimal2): BigDecimal = {
    am.amount
  }
  implicit def bigDecimalOptionToBigDecimal2Option(db: Option[BigDecimal]): Option[BigDecimal2] = {
    db.map(BigDecimal2(_))
  }
}
