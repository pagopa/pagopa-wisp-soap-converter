package eu.sia.pagopa.common.util.log

import com.fasterxml.jackson.core.JsonGenerator
import net.logstash.logback.composite.JsonWritingUtils

import java.math.BigInteger

object CustomJsonWritingUtils extends JsonWritingUtils {

  def writeNumberField(generator: JsonGenerator, fieldName: String, fieldValue: BigInteger): Unit = {
    if (JsonWritingUtils.shouldWriteField(fieldName)) {
      generator.writeFieldName(fieldName)
      generator.writeNumber(fieldValue)
    }
  }

}
