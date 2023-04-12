package eu.sia.pagopa.common.util

import akka.event._
import org.slf4j.MDC

class NodoLogger(_log: LoggingAdapter) {

  private val log: LoggingAdapter = _log

  private final val LOG_MAX_LENGTH = 10000
  private final val PARTIAL_LOG = "partialLog"

  private def _doLog(msg: String, logf: String => Unit): Unit = {
    if (msg.length > LOG_MAX_LENGTH) {
      msg
        .grouped(LOG_MAX_LENGTH)
        .zipWithIndex
        .foreach({ case (s: String, i: Int) =>
          MDC.put(PARTIAL_LOG, s"${i + 1}")
          logf(s)
        //MDC.remove(PARTIAL_LOG)
        })
    } else {
      MDC.put(PARTIAL_LOG, "0")
      logf(msg)
      //MDC.remove(PARTIAL_LOG)
    }
  }

  private def _doLog(cause: Throwable, msg: String, logf: (Throwable, String) => Unit): Unit = {
    if (msg.length > LOG_MAX_LENGTH) {
      msg
        .grouped(LOG_MAX_LENGTH)
        .zipWithIndex
        .foreach({ case (s: String, i: Int) =>
          MDC.put(PARTIAL_LOG, s"${i + 1}")
          logf(cause, s)
        //MDC.remove(PARTIAL_LOG)
        })
    } else {
      MDC.put(PARTIAL_LOG, "0")
      logf(cause, msg)
      //MDC.remove(PARTIAL_LOG)
    }
  }

  def info(message: String): Unit = {
    _doLog(message, s => { log.info(s) })
  }

  def info(message: String, marker: String): Unit = {
    _doLog(message, s => {
      log.info(s)
    })
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

  def isInfoEnabled: Boolean = {
    log.isInfoEnabled
  }

  def isDebugEnabled: Boolean = {
    log.isDebugEnabled
  }

}
