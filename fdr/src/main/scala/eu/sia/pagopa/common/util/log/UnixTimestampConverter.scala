package eu.sia.pagopa.common.util.log

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class UnixTimestampConverter extends ClassicConverter {
  override def convert(event: ILoggingEvent): String = {
    UnixUtil.unixTime().toString
  }
}
