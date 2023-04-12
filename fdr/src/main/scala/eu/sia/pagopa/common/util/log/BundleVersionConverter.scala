package eu.sia.pagopa.common.util.log

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import eu.sia.pagopa.BuildInfo

class BundleVersionConverter extends ClassicConverter {
  override def convert(event: ILoggingEvent): String = BuildInfo.version
}
