package eu.sia.pagopa.mains

import eu.sia.pagopa.Main

object MainTestJob extends App with MainTestUtil {

  object Testjobs extends Enumeration {
    val ftpUpload = Value
  }

  override def host = "127.0.0.1"

  Main.main(Array(Testjobs.ftpUpload.toString))
}
