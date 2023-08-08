package eu.sia.pagopa.common.util.azurehubevent

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.message.{CategoriaEvento, ReExtra, ReRequest, SottoTipoEvento}
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.{Constant, NodoLogger, Util}
import org.slf4j.MDC
import spray.json.DefaultJsonProtocol._
import spray.json.{JsString, _}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Appfunction {

  val sessionId = "session-id"

  private val reFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def formatDate(date: LocalDateTime): String = {
    reFormat.format(date)
  }

  type ReEventFunc = (ReRequest, NodoLogger, ConfigData) => Future[Unit]
  type ContainerBlobFunc = (String, String, NodoLogger) => Future[Unit]
  type QueueAddFunc = (String, String, String, NodoLogger) => Future[Unit]

  def defaultOperation(request: ReRequest, log: NodoLogger, reXmlLog: Boolean, reJsonLog: Boolean, data: ConfigData)(implicit ec: ExecutionContext): Unit = {
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

  private def formatHeaders(headersOpt: Option[Seq[(String, String)]]): String = {
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

  private def param(s: Option[String]): String = {
    s"[${s.getOrElse(Constant.UNKNOWN)}]"
  }

  private def fmtMessage(re: Re, reExtra: Option[ReExtra]): Try[String] = {
    Try({
      if (re.categoriaEvento == CategoriaEvento.INTERFACCIA.toString) {
        val mod = if (re.esito.isDefined) {
          if ((re.esito.contains(EsitoRE.RICEVUTA.toString) || re.esito.contains(EsitoRE.RICEVUTA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.REQ.toString) {
            s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("n.a")}] DETTAGLIO[Il nodo ha ricevuto un messaggio]"
          } else if ((re.esito.contains(EsitoRE.INVIATA.toString) || re.esito.contains(EsitoRE.INVIATA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.RESP.toString) {
            s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("n.a")}] DETTAGLIO[Il nodo ha risposto al messaggio ricevuto]"
          } else if ((re.esito.contains(EsitoRE.INVIATA.toString) || re.esito.contains(EsitoRE.INVIATA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.REQ.toString) {
            s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("n.a")}] DETTAGLIO[Il nodo ha inviato un messaggio]"
          } else if ((re.esito.contains(EsitoRE.RICEVUTA.toString) || re.esito.contains(EsitoRE.RICEVUTA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.RESP.toString) {
            s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("n.a")}] DETTAGLIO[Il nodo ha ricevuto la risposta del messaggio inviato]"
          } else {
            s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("n.a")}] DETTAGLIO[Tipo di REQ/RESP non identificata per sotto tipo evento non valido]"
          }
        } else {
          s"TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] FRUITORE[${re.fruitore.getOrElse("n.a")}] EROGATORE[${re.erogatore.getOrElse("n.a")}] ESITO[n.a] DETTAGLIO[Tipo di REQ/RESP non identificata per esito mancante]"
        }
        val elapsed = if (re.sottoTipoEvento == SottoTipoEvento.RESP.toString) {
          s"${param(reExtra.flatMap(a => a.elapsed.map(_.toString)))}"
        } else {
          s"[${Constant.UNKNOWN}]"
        }
        s"""Re Request => $mod
        httpUri: ${param(reExtra.flatMap(a => a.uri))}
        httpHeaders: ${param(reExtra.map(a => formatHeaders(Some(a.headers))))}
        httpStatusCode: ${param(reExtra.flatMap(a => a.statusCode.map(_.toString)))}
        elapsed: $elapsed
        payload: ${
          param(re.payload.map(as => {
            Util.obfuscate(new String(as))
          }))
        }"""
      } else {
        s"Re Request => TIPO_EVENTO[${re.sottoTipoEvento}/${re.tipoEvento.getOrElse("n.a")}] ESITO[${re.esito.getOrElse("ESITO non presente")}] STATO[${re.status.getOrElse("STATO non presente")}]"
      }
    })
  }

  private def fmtMessageJson(re: Re, reExtra: Option[ReExtra], data: ConfigData): Try[String] = {
    Try({
      val nd = "nd"
      val (isServerRequest, isServerResponse, isClientRequest, isClientResponse, caller, httpType, subject, subjectDescr, isKO) = if (re.categoriaEvento == CategoriaEvento.INTERFACCIA.toString) {
        if (re.esito.isDefined) {
          if ((re.esito.contains(EsitoRE.RICEVUTA.toString) || re.esito.contains(EsitoRE.RICEVUTA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.REQ.toString) {
            (true, false, false, false, Some(Constant.SERVER), Some(Constant.REQUEST), Some(re.fruitore.getOrElse(nd)), Some(re.fruitoreDescr.getOrElse(nd)), false)
          } else if ((re.esito.contains(EsitoRE.INVIATA.toString) || re.esito.contains(EsitoRE.INVIATA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.RESP.toString) {
            (false, true, false, false, Some(Constant.SERVER), Some(Constant.RESPONSE), Some(re.fruitore.getOrElse(nd)), Some(re.fruitoreDescr.getOrElse(nd)), false)
          } else if ((re.esito.contains(EsitoRE.INVIATA.toString) || re.esito.contains(EsitoRE.INVIATA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.REQ.toString) {
            (false, false, true, false, Some(Constant.CLIENT), Some(Constant.REQUEST), Some(re.erogatore.getOrElse(nd)), Some(re.erogatoreDescr.getOrElse(nd)), re.esito.contains(EsitoRE.INVIATA_KO.toString))
          } else if ((re.esito.contains(EsitoRE.RICEVUTA.toString) || re.esito.contains(EsitoRE.RICEVUTA_KO.toString)) && re.sottoTipoEvento == SottoTipoEvento.RESP.toString) {
            (false, false, false, true, Some(Constant.CLIENT), Some(Constant.RESPONSE), Some(re.erogatore.getOrElse(nd)), Some(re.erogatoreDescr.getOrElse(nd)), re.esito.contains(EsitoRE.RICEVUTA_KO.toString))
          } else {
            (false, false, false, false, Some(nd), Some(nd), Some(nd), Some(nd), false)
          }
        } else {
          (false, false, false, false, Some(nd), Some(nd), Some(nd), Some(nd), false)
        }
      } else {
        (false, false, false, false, None, None, None, None, false)
      }

      val isSoapProtocol = reExtra.exists(r => r.soapProtocol)
      val categoriaEvento = re.categoriaEvento
      val statusCode = reExtra.flatMap(_.statusCode)
      val uri = reExtra.flatMap(_.uri)
      val payload = re.payload.map(v => new String(v, Constant.UTF_8))
      val (isSuccess, faultCode, faultString, faultDescription, jsonError) = if (isSoapProtocol) {
        val fault = payload.flatMap(v => getFaultFromXml(v))
        fault match {
          case Some((code, str, descr)) if !isKO =>
            (false, Some(code), str, descr, None)
          case None if isKO =>
            (false, None, None, None, payload)
          case None =>
            (true, None, None, None, None)
          case _ => ???
        }
      } else if (!isSoapProtocol) {
        val (faultJsonCode, faultJsonString, faultJsonDescription) = getFaultFromJson(httpType, re.businessProcess, payload)
        if (statusCode.contains(200) && faultJsonCode.isDefined && httpType.contains(Constant.RESPONSE)) {
          (false, Some(faultJsonCode.getOrElse("")), Some(faultJsonString.getOrElse("")), Some(faultJsonDescription.getOrElse("")), payload)
        } else if (!statusCode.contains(200) && httpType.contains(Constant.RESPONSE)) {
          (false, Some(faultJsonCode.getOrElse("")), Some(faultJsonString.getOrElse("")), Some(faultJsonDescription.getOrElse("")), None)
        } else {
          (true, None, None, None, None)
        }
      } else {
        (true, None, None, None, None)
      }
      val esitoComplex = if (isSuccess) {
        s" [esito:OK]"
      } else {
        s" [esito:KO]${faultCode.map(v => s" [faultCode:$v]").getOrElse("")}${faultString.map(v => s" [faultString:$v]").getOrElse("")}${faultDescription.map(v => s" [faultDescription:$v]").getOrElse("")}${jsonError.map(v => s" [genericError:$v]").getOrElse("")}"
      }
      val elapsed = reExtra.flatMap(_.elapsed)
      val soapAction = reExtra.flatMap(h => h.headers.find(_._1 == "SOAPAction").map(_._2))
      val businessProcess = re.businessProcess
      val tipoEvento = re.tipoEvento
      val internalMessage = if (re.categoriaEvento == CategoriaEvento.INTERFACCIA.toString) {
        if (re.esito.isDefined) {
          if (isServerRequest) {
            s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: messaggio da [subject:${subject.getOrElse("")}]"
          } else if (isServerResponse) {
            s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: risposta a [subject:${subject.getOrElse("")}]${elapsed.map(v => s" [elapsed:${v}ms]").getOrElse("")}${statusCode.map(v => s" [statusCode:$v]").getOrElse("")}$esitoComplex"
          } else if (isClientRequest) {
            s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: messaggio a [subject:${subject.getOrElse("")}]${uri.map(v => s" [uri:$v]").getOrElse("")}"
          } else if (isClientResponse) {
            s"${caller.getOrElse("")} --> ${httpType.getOrElse("")}: risposta da [subject:${subject.getOrElse("")}]${elapsed.map(v => s" [elapsed:${v}ms]").getOrElse("")}${statusCode.map(v => s" [statusCode:$v]").getOrElse("")}$esitoComplex${uri.map(v => s" [from-uri:$v]").getOrElse("")}"
          } else {
            "Tipo di REQ/RESP non identificata per sotto tipo evento non valido"
          }
        } else {
          "Tipo di REQ/RESP non identificata per esito mancante"
        }
      } else {
        s"Cambio stato in [${re.status.getOrElse(nd)}]"
      }

      val psp = re.psp
      val pspDescr = re.pspDescr
      val idDominio = re.idDominio
      val pa = idDominio.flatMap(v => data.creditorInstitutions.get(v).map(_.description))

      val stringBuilder = new StringBuilder("{")
      stringBuilder.append(s""""internalMessage":${JsString(internalMessage)}""")
      stringBuilder.append(s""","categoriaEvento":"$categoriaEvento"""")
      caller.foreach(v => stringBuilder.append(s""","caller":"$v""""))
      httpType.foreach(v => stringBuilder.append(s""","httpType":"$v""""))
      stringBuilder.append(s""","isSoapProtocol":$isSoapProtocol""")
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
      jsonError.foreach(v => stringBuilder.append(s""","genericError":${JsString(v)}"""))
      businessProcess.foreach(v => stringBuilder.append(s""","businessProcess":"$v""""))
      tipoEvento.foreach(v => stringBuilder.append(s""","tipoEvento":"$v""""))
      subject.foreach(v => stringBuilder.append(s""","subject":"$v""""))
      subjectDescr.foreach(v => stringBuilder.append(s""","subjectDescr":"$v""""))
      psp.foreach(v => stringBuilder.append(s""","psp":"$v""""))
      pspDescr.foreach(v => stringBuilder.append(s""","pspDescr":"$v""""))
      idDominio.foreach(v => stringBuilder.append(s""","idDominio":"$v""""))
      pa.map(v => stringBuilder.append(s""","paDescr":"$v""""))
      stringBuilder.append("}")
      stringBuilder.toString()
    })
  }

  val regFault = "<fault>[\\s\\S]*?<faultCode>([\\s\\S]*?)</faultCode>[\\s\\S]*?</fault>".r
  val regFaultString = "<faultString>([\\s\\S]*?)</faultString>".r
  val regDesc = "<description>([\\s\\S]*?)</description>".r

  private def getFaultFromXml(xml: String) = {
    regFault.findFirstMatchIn(xml).flatMap(s => {
      s.subgroups.headOption.map(fc => {
        (fc, regFaultString.findFirstMatchIn(s.matched).flatMap(_.subgroups.headOption), regDesc.findFirstMatchIn(s.matched).flatMap(_.subgroups.headOption))
      })
    })
  }

  private def getFaultFromJson(httpType: Option[String], businessProcess: Option[String], json: Option[String]) = {
    if (httpType.isDefined && httpType.contains(Constant.RESPONSE)) {
      val jsValue = json.map(v => v.parseJson.asJsObject)
      businessProcess match {
        case Some(value) if (value == "notifyFlussoRendicontazione") =>
          val error = jsValue.flatMap(_.getFields("error").headOption).map(_.convertTo[String]).map(v => s"error=[$v]")
          if (error.isDefined) {
            (
              Some("<REST_NO_FAULT_CODE>"),
              Some("<REST_NO_FAULT_STRING>"),
              Some(List(None, error, None, None).flatten.mkString(", ")),
            )
          } else {
            (None, None, None)
          }
        case _ =>
          (None, None, None)
      }
    } else {
      (None, None, None)
    }
  }


}
