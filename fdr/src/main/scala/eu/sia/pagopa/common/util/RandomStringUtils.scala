package eu.sia.pagopa.common.util

import scala.util.Random

object RandomStringUtils {

  def randomNumeric(size: Int): String = {
    Random.alphanumeric.filter(_.isDigit).take(size).mkString("")
  }

}
