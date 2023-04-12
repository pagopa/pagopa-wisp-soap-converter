package eu.sia.pagopa.common.util

import java.math.BigInteger

object HashUtils {

  import java.security.MessageDigest
  def hashString(s: String): String = {
    String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))))
  }

}
