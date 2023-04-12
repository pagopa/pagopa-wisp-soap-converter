package eu.sia.pagopa.mains

import eu.sia.pagopa.Main

object MainTest extends App with MainTestUtil {
  override def host = "0.0.0.0"
  Main.main(Array())
}
