package it.gov.pagopa.common.util.xml

import it.gov.pagopa.common.util.Constant
import scalaxb.Base64Binary

import java.util.Base64

object XmlUtil {

  object StringBase64Binary {

    def encodeBase64(s: Array[Byte]): Base64Binary = {
      Base64Binary(encodeBase64ToString(s))
    }

    def encodeBase64ToString(s: Array[Byte]): String = {
      new String(encodeBase64ToArray(s), Constant.UTF_8)
    }

    def encodeBase64ToArray(s: Array[Byte]): Array[Byte] = {
      Base64.getEncoder.encode(s)
    }

    def decodeBase64(base64Binary: Base64Binary): String =
      new String(decodeBase64ToByteArray(base64Binary.toString), Constant.UTF_8)

    def decodeBase64ToByteArray(encodedString: String): Array[Byte] =
      Base64.getMimeDecoder.decode(encodedString.getBytes(Constant.UTF_8))
  }

}
