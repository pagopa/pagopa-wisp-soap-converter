package eu.sia.pagopa.common.util.azurehubevent

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.message.ReRequest
import eu.sia.pagopa.common.util.NodoLogger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

object Appfunction {

  val sessionId = "session-id"

  private val reFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def formatDate(date: LocalDateTime): String = {
    reFormat.format(date)
  }

  type ReEventFunc = (ReRequest, NodoLogger, ConfigData) => Future[Unit]

}
