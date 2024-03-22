package it.gov.pagopa.common.util

import it.gov.pagopa.common.util.xml.XmlUtil
import scalaxb.Base64Binary

import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.util.Try

object StringUtils {

  implicit class Utf8String(str: String) {
    def getUtf8Bytes: Array[Byte] = str.getBytes(Constant.UTF_8)
  }

  def getStringDecoded(src: Base64Binary, checkCharset: Boolean, charset: Option[Charset] = Some(Constant.UTF_8)) =
    getStringDecodedByString(src.toString, checkCharset, charset)

  def getStringDecodedByString(src: String, checkCharset: Boolean, charset: Option[Charset] = Some(Constant.UTF_8)) = {
    Try({
      val decoded = XmlUtil.StringBase64Binary.decodeBase64ToByteArray(src)
      if (checkCharset) {
        val dec = charset.get.newDecoder()
        dec.decode(ByteBuffer.wrap(decoded))
        new String(decoded, charset.get)
      } else {
        new String(decoded, charset.get)
      }
    })
  }
}
