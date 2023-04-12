package eu.sia.pagopa.common.util

import akka.http.scaladsl.model.Uri

import scala.util.{ Failure, Success, Try }

object UrlUtil {

  def convertFullPath2PathAndQueryString(path: String): (String, Map[String, String]) = {
    if (!path.contains("?")) {
      (path, Map())
    } else {
      val serviceSplit = path.split("\\?")
      val service = serviceSplit(0)
      val queryString = convertQueryString2Map(serviceSplit(1))
      (service, queryString)
    }
  }

  def convertQueryString2Map(qs: String): Map[String, String] = {
    val qsFixed = if (qs.head == '?') qs.substring(1) else qs
    qsFixed
      .split("&")
      .map(c => {
        val spl = c.split("=")
        if (spl.length > 1) {
          spl.head -> spl.last
        } else {
          spl.head -> ""
        }
      })
      .toMap
  }

  private def fixServizio(servizio: String): String =
    if (servizio.nonEmpty && servizio.head == '/') servizio else s"/$servizio"

  case class HttpFixedReq(scheme: String, host: String, port: Long, path: String, queryString: String)

  def fixValueForURIFromDB(protocolloOpt: Option[String], ipOpt: Option[String], portaOpt: Option[Long], servizioOpt: Option[String], queryString: Map[String, String])(implicit
      log: NodoLogger
  ): Try[HttpFixedReq] = {
    (protocolloOpt, ipOpt, portaOpt, servizioOpt) match {
      case (Some(protocollo), Some(ip), Some(port), Some(servizio)) =>
        Success(HttpFixedReq(protocollo.toLowerCase(), ip, port, fixServizio(servizio), Uri.Query(queryString).toString()))

      case _ =>
        val p = s"protocollo:$protocolloOpt,ip:$ipOpt,porta:$portaOpt,servizio:$servizioOpt"
        log.info(s"URL non costruita per parametro/i mancante/i ($p)")
        Failure(new Exception(s"URL non costruita per parametro/i mancante/i ($p)"))
    }
  }

}
