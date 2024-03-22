package it.gov.pagopa.tests.mains

import it.gov.pagopa.Main

object MainTest extends App with MainTestUtil {
  override def host = "0.0.0.0"
  Main.main(Array())
}
