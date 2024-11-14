package it.gov.pagopa.common.util.azure

import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.azure.cosmos.EventCategory
import it.gov.pagopa.common.util.{AppLogger, Constant, Util}
import org.slf4j.MDC
import spray.json.JsString

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Appfunction {

  type ReEventFunc = (ReRequest, AppLogger, ConfigData) => Future[Unit]
  val sessionId = "session-id"
  val regFault = "<fault>[\\s\\S]*?<faultCode>([\\s\\S]*?)</faultCode>[\\s\\S]*?</fault>".r
  val regFaultString = "<faultString>([\\s\\S]*?)</faultString>".r
  val regDesc = "<description>([\\s\\S]*?)</description>".r
  private val reFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

  def defaultOperation(request: ReRequest, log: AppLogger, reXmlLog: Boolean, reJsonLog: Boolean, data: ConfigData)(implicit ec: ExecutionContext): Unit = {
    MDC.put(Constant.MDCKey.DATA_ORA_EVENTO, Appfunction.formatDate(request.re.insertedTimestamp))

    if (reXmlLog) {
      Future(
        fmtMessage(request.re, request.reExtra)
          .map(msg => {
            MDC.put(Constant.RE_UID, request.re.uniqueId)
            MDC.put(Constant.RE_XML_LOG, "true")
            val elapsed = request.reExtra.flatMap(b => b.elapsed)
            elapsed match {
              case Some(a) => MDC.put(Constant.MDCKey.ELAPSED, a.toString)
              case _ =>
            }
            log.info(msg)
            elapsed match {
              case Some(_) => MDC.remove(Constant.MDCKey.ELAPSED)
              case _ =>
            }
            MDC.remove(Constant.RE_UID)
            MDC.remove(Constant.RE_XML_LOG)
          })
          .recover({ case e: Throwable =>
            log.error(e, "Format message error")
          })
      )
    }
    if (reJsonLog) {
      Future(
        fmtMessageJson(request.re, request.reExtra, data)
          .map(msg => {
            MDC.put(Constant.RE_UID, request.re.uniqueId)
            MDC.put(Constant.RE_JSON_LOG, "true")
            val elapsed = request.reExtra.flatMap(b => b.elapsed)
            elapsed match {
              case Some(a) => MDC.put(Constant.MDCKey.ELAPSED, a.toString)
              case _ =>
            }
            log.info(msg)
            elapsed match {
              case Some(_) => MDC.remove(Constant.MDCKey.ELAPSED)
              case _ =>
            }
            MDC.remove(Constant.RE_UID)
            MDC.remove(Constant.RE_JSON_LOG)
          })
          .recover({ case e: Throwable =>
            log.error(e, "Format message error")
          })
      )
    }
  }

  def formatDate(date: Instant): String = {
    reFormat.format(date)
  }

  def fmtMessage(re: Re, reExtra: Option[ReExtra]): Try[String] = {
    Try({
      if (re.eventCategory == EventCategory.INTERFACE) {
        s"""Re Request =>
           |httpUri: ${param(reExtra.flatMap(a => a.uri))}
           |httpHeaders: ${param(reExtra.map(a => formatHeaders(Some(a.requestHeaders))))}
           |httpStatusCode: ${param(reExtra.flatMap(a => a.statusCode.map(_.toString)))}
           |payload: ${
          param(re.requestPayload.map(as => {
            Util.obfuscate(new String(as))
          }))
        }""".stripMargin
      } else {
        s"Re Request => ESITO[${re.outcome}] STATO[${re.status.getOrElse("STATO non presente")}]"
      }
    })
  }

  def formatHeaders(headersOpt: Option[Seq[(String, String)]]): String = {
    headersOpt
      .map(d => {
        if (d.isEmpty) {
          Constant.UNKNOWN
        } else {
          d.map(h => s"${h._1}=${h._2}").mkString(",")
        }
      })
      .getOrElse(Constant.UNKNOWN)
  }

  def param(s: Option[String]): String = {
    s"[${s.getOrElse(Constant.UNKNOWN)}]"
  }

  def fmtMessageJson(re: Re, reExtra: Option[ReExtra], data: ConfigData): Try[String] = {
    Try({
      val nd = "nd"
      val (isServerRequest, isServerResponse, caller, httpType, subject, subjectDescr) = if (re.eventCategory == EventCategory.INTERFACE) {
        (false, false, Some(nd), Some(nd), Some(nd), Some(nd))
      } else {
        (false, false, None, None, None, None)
      }
      val categoriaEvento = re.eventCategory
      val statusCode = reExtra.flatMap(_.statusCode)
      val payload = re.requestPayload.map(v => new String(v, Constant.UTF_8))
      val (isSuccess, faultCode, faultString, faultDescription) = {
        val fault = payload.flatMap(v => getFaultFromXml(v))
        fault match {
          case Some((code, str, descr)) =>
            (false, Some(code), str, descr)
          case None =>
            (true, None, None, None)
        }
      }
      val esitoComplex = if (isSuccess) {
        s" [esito:OK]"
      } else {
        s" [esito:KO]${faultCode.map(v => s" [faultCode:$v]").getOrElse("")}${faultString.map(v => s" [faultString:$v]").getOrElse("")}${faultDescription.map(v => s" [faultDescription:$v]").getOrElse("")}"
      }
      val elapsed = reExtra.flatMap(_.elapsed)
      val soapAction = reExtra.flatMap(h => h.requestHeaders.find(_._1 == "SOAPAction").map(_._2))
      val businessProcess = re.businessProcess
      val internalMessage = if (re.eventCategory == EventCategory.INTERFACE) {
        if (isServerRequest) {
          s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: messaggio da [subject:${subject.getOrElse("")}]"
        } else if (isServerResponse) {
          s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: risposta a [subject:${subject.getOrElse("")}]${elapsed.map(v => s" [elapsed:${v}ms]").getOrElse("")}${statusCode.map(v => s" [statusCode:$v]").getOrElse("")}$esitoComplex"
        } else {
          "Tipo di REQ/RESP non identificata per sotto tipo evento non valido"
        }
      } else {
        s"Cambio stato in [${re.status.getOrElse(nd)}]"
      }

      val psp = re.psp
      val idDominio = re.domainId
      val pa = idDominio.flatMap(v => data.creditorInstitutions.get(v).map(_.description))

      val stringBuilder = new StringBuilder("{")
      stringBuilder.append(s""""internalMessage":${JsString(internalMessage)}""")
      stringBuilder.append(s""","categoriaEvento":"$categoriaEvento"""")
      caller.foreach(v => stringBuilder.append(s""","caller":"$v""""))
      httpType.foreach(v => stringBuilder.append(s""","httpType":"$v""""))
      soapAction.foreach(v => stringBuilder.append(s""","soapAction":${JsString(v)}"""))
      elapsed.foreach(v => stringBuilder.append(s""","elapsed":$v"""))
      statusCode.foreach(v => stringBuilder.append(s""","statusCode":$v"""))
      if (isSuccess) {
        stringBuilder.append(s""","esito":"OK"""")
      } else {
        stringBuilder.append(s""","esito":"KO"""")
      }
      faultCode.foreach(v => stringBuilder.append(s""","faultCode":${JsString(v)}"""))
      faultString.foreach(v => stringBuilder.append(s""","faultString":${JsString(v)}"""))
      faultDescription.foreach(v => stringBuilder.append(s""","faultDescription":${JsString(v)}"""))
      businessProcess.foreach(v => stringBuilder.append(s""","businessProcess":"$v""""))
      subject.foreach(v => stringBuilder.append(s""","subject":"$v""""))
      subjectDescr.foreach(v => stringBuilder.append(s""","subjectDescr":"$v""""))
      psp.foreach(v => stringBuilder.append(s""","psp":"$v""""))
      idDominio.foreach(v => stringBuilder.append(s""","idDominio":"$v""""))
      pa.map(v => stringBuilder.append(s""","paDescr":"$v""""))
      stringBuilder.append("}")
      stringBuilder.toString()
    })
  }

  def getFaultFromXml(xml: String) = {
    regFault.findFirstMatchIn(xml).flatMap(s => {
      s.subgroups.headOption.map(fc => {
        (fc, regFaultString.findFirstMatchIn(s.matched).flatMap(_.subgroups.headOption), regDesc.findFirstMatchIn(s.matched).flatMap(_.subgroups.headOption))
      })
    })
  }

}
