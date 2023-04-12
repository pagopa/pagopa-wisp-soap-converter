package eu.sia.pagopa.common.util

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{StatusCodes, Uri, ContentType => _}
import akka.routing.RoundRobinGroup
import com.typesafe.config.Config
import eu.sia.pagopa.common.exception.RestException
import it.pagopa.config.{Channel, Station, StationCreditorInstitution}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.util.{Base64, Properties}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object Util {

  def now(): LocalDateTime = {
    LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
  }

  def getNoticeNumberData(noticeNumber: String): (String, Option[Long], Option[Int], Option[Long]) = {
    val auxDigit = noticeNumber.substring(0, 1).toLong
    val segregazione = auxDigit match {
      case 3 | 4 => Some(noticeNumber.substring(1, 3).toLong)
      case _     => None
    }
    val progressivo = auxDigit match {
      case 0 => Some(noticeNumber.substring(1, 3).toInt)
      case _ => None
    }
    val auxValue = auxDigit match {
      case 0 | 3 => None
      case _     => Option(auxDigit)
    }
    val iuv = if (auxDigit == 0) {
      noticeNumber.substring(3)
    } else {
      noticeNumber.substring(1)
    }
    (iuv, segregazione, progressivo, auxValue)
  }

  def getNoticeNumberDataByIuv(iuv: String, paStazionePa: StationCreditorInstitution): (String) = {
    val auxDigit = paStazionePa.auxDigit
    auxDigit match {
      case None if iuv.length == 15 => {
        val progressivo = f"${paStazionePa.applicationCode.getOrElse(0L)}%02d"
        s"0${progressivo}$iuv"
      }
      case None if (iuv.length == 17) => s"3$iuv"
      case Some(ad)                   => s"$ad$iuv"
      case _ =>
        throw RestException(s"iuv length must be 15 or 17,actual ${iuv.length}", s"iuv length must be 15 or 17,actual ${iuv.length}", StatusCodes.BadRequest.intValue)
    }
  }

  def anyOptionDefined(s: Option[Any]*): Boolean = {
    s.flatten.nonEmpty
  }
  def allOptionsEmpty(s: Option[Any]*): Boolean = {
    s.flatten.isEmpty
  }

  val OBFUSCATE_REGEX_MAP = Seq(new Regex("(<(?:\\w*?:{0,1})password(?:.*)>)(.*)(<\\/(?:\\w*?:{0,1})password(?:.*)>)") -> "$1XXXXXXXXXX$3")

  def obfuscate(str: String): String = {
    //nel nostro caso che abbiamo xml con un solo tag password va bene se dovessero essercene di più va ripensata
    OBFUSCATE_REGEX_MAP.foldLeft(str)((s, rexT) => {
      rexT._1.replaceFirstIn(s, rexT._2)
    })
  }

  def logPayload(log: NodoLogger, payload: Option[String]): Unit = {
    if (log.isDebugEnabled) {
      log.debug(payload.map(Util.obfuscate).getOrElse("[NO PAYLOAD]"))
    }
  }

  def createLocalRouter(actorName: String, routerName: String, roles: Set[String] = Set())(implicit system: ActorSystem): ActorRef = {
    system.actorOf(RoundRobinGroup(List(s"/user/$actorName")).props(), name = routerName)
  }

  def faultXmlResponse(faultcode: String, faultstring: String, detail: Option[String]) =
    s"""<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><soap:Fault><faultcode>$faultcode</faultcode><faultstring>$faultstring</faultstring>${detail
      .map(x => s"<detail>$x</detail>")
      .getOrElse("")}</soap:Fault></soap:Body></soap:Envelope>"""

  def getActorRouterName(primitiva: String, sender: Option[String]): String = {
    s"$primitiva${sender.map(sen => s"_$sen").getOrElse("")}"
  }

  implicit class configMapperOps(config: Config) {
    import scala.jdk.CollectionConverters._
    def toMap: Map[String, AnyRef] =
      config.entrySet().asScala.map(pair => (pair.getKey, config.getAnyRef(pair.getKey))).toMap

    def toProperties: Properties = {
      val properties = new Properties()
      properties.putAll(config.toMap.asJava)
      properties
    }
  }

  val SHA1Digest: MessageDigest = java.security.MessageDigest.getInstance("SHA-1")
  def sha1(name: String): String = Base64.getEncoder.encodeToString(SHA1Digest.digest(name.getBytes))

  def mapToJson(map: Map[String, Any]): String = {
    map
      .map { i =>
        def quote(x: Any): String = "\"" + x + "\""
        val key: String = quote(i._1)
        val value: String = i._2 match {
          case elem: Seq[_] =>
            elem
              .map {
                case ee: Map[_, _] => mapToJson(ee.asInstanceOf[Map[String, Any]])
                case _             => quote(_)
              }
              .mkString("[", ",", "]")
          case elem: Option[_] =>
            elem.map(quote).getOrElse("null")
          case elem: Map[_, _] =>
            mapToJson(elem.asInstanceOf[Map[String, Any]])
          case elem =>
            quote(elem)
        }
        s"$key : $value"
      }
      .mkString("{", ", ", "}")
  }

  def zipContent(bytes: Array[Byte]) = {
    val bais = new ByteArrayOutputStream(bytes.length)
    val gzipOut = new GZIPOutputStream(bais)
    gzipOut.write(bytes)
    gzipOut.close()
    val compressed = bais.toByteArray
    bais.close()
    compressed
  }

  def unzipContent(compressed: Array[Byte]) = {
    Try {
      val bais = new ByteArrayInputStream(compressed)
      new GZIPInputStream(bais).readAllBytes()
    }
  }

  def getRedirectUrl(channel:Channel,extraParameters: Map[String, String])(implicit log: NodoLogger): Try[String] = {
    val queryStringWithExtraParmaeters: Map[String, String] = channel.redirect.queryString match {
      case Some(qs) if qs.nonEmpty =>
        UrlUtil.convertQueryString2Map(qs) ++ extraParameters
      case _ =>
        extraParameters
    }
    UrlUtil
      .fixValueForURIFromDB(channel.redirect.protocol, channel.redirect.ip, channel.redirect.port, channel.redirect.path, queryStringWithExtraParmaeters)
      .flatMap(t => {
        Success(Uri.from(scheme = t.scheme, host = t.host, port = t.port.toInt, path = t.path, queryString = Some(t.queryString)))
      })
      .map(v => {
        val url = v.toString()
        log.debug(s"getRedirectUrl [$url]")
        url
      })
      .recoverWith({ case e: Throwable =>
        log.info("Errore costruzione URL")
        Failure(e)
      })
  }

  def getRedirectUrl(station: Station, extraParameters: Map[String, String])(implicit log: NodoLogger): Try[String] = {
    val queryStringWithExtraParmaeters: Map[String, String] = station.redirect.queryString match {
      case Some(qs) if qs.nonEmpty =>
        UrlUtil.convertQueryString2Map(qs) ++ extraParameters
      case _ =>
        extraParameters
    }
    UrlUtil
      .fixValueForURIFromDB(station.redirect.protocol, station.redirect.ip, station.redirect.port, station.redirect.path, queryStringWithExtraParmaeters)
      .flatMap(t => {
        Success(Uri.from(scheme = t.scheme, host = t.host, port = t.port.toInt, path = t.path, queryString = Some(t.queryString)))
      })
      .map(v => {
        val url = v.toString()
        log.debug(s"getRedirectUrl [$url]")
        url
      })
      .recoverWith({ case e: Throwable =>
        log.info("Errore costruzione URL")
        Failure(e)
      })
  }

}
