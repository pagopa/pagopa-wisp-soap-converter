package eu.sia.pagopa.common.util.xml

import eu.sia.pagopa.common.util.Constant
import scalaxb.Base64Binary

import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.Base64
import javax.xml.datatype.XMLGregorianCalendar

object XmlUtil {

  object StringBase64Binary {
    def encodeBase64(s: Array[Byte]): Base64Binary = {
      Base64Binary(encodeBase64ToString(s))
    }

    def encodeBase64ToString(s: Array[Byte]): String = {
      new String(encodeBase64ToArray(s), Constant.UTF_8)
    }

    def encodeBase64ToBase64(s: Array[Byte]): Base64Binary = {
      Base64Binary(encodeBase64ToString(s))
    }

    def encodeBase64(s: String): Base64Binary = {
      Base64Binary(encodeBase64ToString(s.getBytes(Constant.UTF_8)))
    }

    def encodeBase64ToArray(s: Array[Byte]): Array[Byte] = {
      Base64.getEncoder.encode(s)
    }

    def decodeBase64(base64Binary: Base64Binary): String =
      new String(decodeBase64ToByteArray(base64Binary.toString), Constant.UTF_8)

    def decodeBase64ByString(encodedString: String): String =
      new String(decodeBase64ToByteArray(encodedString), Constant.UTF_8)

    def decodeBase64ToByteArray(encodedString: String): Array[Byte] =
      Base64.getMimeDecoder.decode(encodedString.getBytes(Constant.UTF_8))
  }

  object XsdDatePattern extends Enumeration {
    val ST_DATE_TIME_GIORNO_ORA, DATE_TIME, DATE, TIME = Value
  }

  object StringXMLGregorianCalendarDate {

    def format(temporal: Temporal, pattern: XsdDatePattern.Value): XMLGregorianCalendar = {
      scalaxb.XMLCalendar(
        DateTimeFormatter
          .ofPattern(pattern match {
            case XsdDatePattern.ST_DATE_TIME_GIORNO_ORA => "yyyy-MM-dd'T'HH:mm:ss"
            case XsdDatePattern.DATE_TIME               => "yyyy-MM-dd'T'HH:mm:ss"
            case XsdDatePattern.DATE                    => "yyyy-MM-dd"
            case XsdDatePattern.TIME                    => "HH:mm:ss"
          })
          .format(temporal)
      )
    }

  }

}
