package it.gov.pagopa.common.util.azure

import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.{AppLogger, Constant}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

object Appfunction {

  val sessionId = "session-id"
  private val reFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def formatDate(date: LocalDateTime): String = {
    reFormat.format(date)
  }
  type ReEventFunc = (ReRequest, AppLogger) => Future[Unit]

}
