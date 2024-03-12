package it.gov.pagopa.tests

import scala.util.Random

object RandomStringUtils {

  def randomNumeric(size: Int): String = {
    Random.alphanumeric.filter(_.isDigit).take(size).mkString("")
  }

  def randomAlphanumeric(size: Int): String = {
    Random.alphanumeric.take(size).mkString("")
  }

}
