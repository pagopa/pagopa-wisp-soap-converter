package it.gov.pagopa.common.util

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentType => _}
import akka.routing.RoundRobinGroup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.zip.GZIPOutputStream
import scala.util.matching.Regex

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

  val OBFUSCATE_REGEX_MAP = Seq(new Regex("(<(?:\\w*?:{0,1})password(?:.*)>)(.*)(<\\/(?:\\w*?:{0,1})password(?:.*)>)") -> "$1XXXXXXXXXX$3")

  def obfuscate(str: String): String = {
    //nel nostro caso che abbiamo xml con un solo tag password va bene se dovessero essercene di più va ripensata
    OBFUSCATE_REGEX_MAP.foldLeft(str)((s, rexT) => {
      rexT._1.replaceFirstIn(s, rexT._2)
    })
  }

  def logPayload(log: AppLogger, payload: Option[String]): Unit = {
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

  val SHA1Digest: MessageDigest = java.security.MessageDigest.getInstance("SHA-1")
  def sha1(name: String): String = Base64.getEncoder.encodeToString(SHA1Digest.digest(name.getBytes))

  def zipContent(bytes: Array[Byte]) = {
    val bais = new ByteArrayOutputStream(bytes.length)
    val gzipOut = new GZIPOutputStream(bais)
    gzipOut.write(bytes)
    gzipOut.close()
    val compressed = bais.toByteArray
    bais.close()
    compressed
  }

//  def unzipContent(compressed: Array[Byte]) = {
//    Try {
//      val bais = new ByteArrayInputStream(compressed)
//      new GZIPInputStream(bais).readAllBytes()
//    }
//  }




}
