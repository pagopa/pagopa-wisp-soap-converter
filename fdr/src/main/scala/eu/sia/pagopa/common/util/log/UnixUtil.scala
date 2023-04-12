package eu.sia.pagopa.common.util.log

import scala.sys.process.Process

object UnixUtil {

  def unixTime(): Long = {
    val res = Process("date +%Y%m%d%H%M%S%N").!!
    //tronco il numero da un formato BigInteger(23) a Long(19) altrimenti lo stack efk non riesce a parsere il messaggio e non collezion log
    val fixedValueForLog = res.trim.substring(0, res.trim.length - 4)
    java.lang.Long.parseLong(fixedValueForLog)
  }

}
