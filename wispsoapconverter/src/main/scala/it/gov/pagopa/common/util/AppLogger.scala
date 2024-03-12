package it.gov.pagopa.common.util

import akka.event._

class AppLogger(_log: LoggingAdapter) {

  private val log: LoggingAdapter = _log

  private def _doLog(msg: String, logf: String => Unit): Unit = {
      logf(msg)
  }

  private def _doLog(cause: Throwable, msg: String, logf: (Throwable, String) => Unit): Unit = {
      logf(cause, msg)
  }

  def info(message: String): Unit = {
    _doLog(message, s => { log.info(s) })
  }

  def debug(message: String): Unit = {
    _doLog(message, s => { log.debug(s) })
  }

  def warn(message: String): Unit = {
    _doLog(message, s => { log.warning(s) })
  }

  def warn(cause: Throwable, message: String): Unit = {
    _doLog(message, s => { log.warning(s"$s\n${cause.getStackTrace.mkString("\n")}") })
  }

  def error(cause: Throwable, message: String): Unit = {
    _doLog(cause, message, (c, s) => { log.error(c, s) })
  }

  def error(message: String): Unit = {
    _doLog(message, s => { log.error(s) })
  }

  def isDebugEnabled: Boolean = {
    log.isDebugEnabled
  }

}
