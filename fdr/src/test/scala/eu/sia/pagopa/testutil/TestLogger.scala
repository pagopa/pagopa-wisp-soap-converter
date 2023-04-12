package eu.sia.pagopa.testutil

import akka.event.LoggingAdapter
import com.typesafe.scalalogging.Logger

class TestLogger extends LoggingAdapter {

  private val log = Logger(this.getClass())

  /** Java API to return the reference to NoLogging
    * @return
    *   The NoLogging instance
    */
  def getInstance = this

  final override def isErrorEnabled = true
  final override def isWarningEnabled = true
  final override def isInfoEnabled = true
  final override def isDebugEnabled = true

  final protected override def notifyError(message: String): Unit = (log.error(message))
  final protected override def notifyError(cause: Throwable, message: String): Unit = (log.error(message, cause))
  final protected override def notifyWarning(message: String): Unit = (log.warn(message))
  final protected override def notifyInfo(message: String): Unit = (log.info(message))
  final protected override def notifyDebug(message: String): Unit = (log.debug(message))
}
