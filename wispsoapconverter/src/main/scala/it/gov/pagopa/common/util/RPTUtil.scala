package it.gov.pagopa.common.util

import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.xml.XmlUtil.StringBase64Binary
import scalaxb.Base64Binary
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, IntestazionePPT}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import scala.util.{Success, Try}
import scala.xml.XML

object RPTUtil {

  def getAdapterEcommerceUri(uri: String, req:SoapRequest, int:IntestazioneCarrelloPPT): String = {
//    s"${uri}${getUniqueKey(req, int)}"
    uri.replaceAll("REPLACE", getUniqueKey(req, int))
  }
  def getAdapterEcommerceUri(uri: String, req:SoapRequest, int:IntestazionePPT): String = {
//    s"${uri}${getUniqueKey(req, int)}"
    uri.replaceAll("REPLACE", getUniqueKey(req, int))
  }

  def getUniqueKey(req:SoapRequest, int:IntestazioneCarrelloPPT) = {
    s"${req.sessionId}"
  }
  def getUniqueKey(req:SoapRequest, int:IntestazionePPT) = {
    s"${req.sessionId}"
  }

  def getRptByBase64Binary(rptEncoded: Base64Binary): Try[CtRichiestaPagamentoTelematico] = {
    Success(scalaxb.fromXML[CtRichiestaPagamentoTelematico](XML.loadString(StringBase64Binary.decodeBase64(rptEncoded))))
  }

//  def Carrello2Str(header:scalaxbmodel.nodoperpa.IntestazioneCarrelloPPT,body: scalaxbmodel.nodoperpa.NodoInviaCarrelloRPT): Try[(String,String)] = {
//      val h = Try(scalaxb.toXML[scalaxbmodel.nodoperpa.IntestazioneCarrelloPPT](header, "IntestazioneCarrelloPPT", scalaxbmodel.paginf.defaultScope))
//      val hh = h.map(v => transform(v.toString))
//      val b = Try(scalaxb.toXML[scalaxbmodel.nodoperpa.NodoInviaCarrelloRPT](body, "NodoInviaCarrelloRPT", scalaxbmodel.paginf.defaultScope))
//      val bb = b.map(v => transform(v.toString))
//    for {
//      header <- hh
//      body <- bb
//    } yield (header,body)
//  }
//
//  def RPT2Str(header:scalaxbmodel.nodoperpa.IntestazionePPT,body: scalaxbmodel.nodoperpa.NodoInviaRPT): Try[(String,String)] = {
//    val h = Try(scalaxb.toXML[scalaxbmodel.nodoperpa.IntestazionePPT](header, "IntestazionePPT", scalaxbmodel.paginf.defaultScope))
//    val hh = h.map(v => transform(v.toString))
//    val b = Try(scalaxb.toXML[scalaxbmodel.nodoperpa.NodoInviaRPT](body, "NodoInviaRPT", scalaxbmodel.paginf.defaultScope))
//    val bb = b.map(v => transform(v.toString))
//    for {
//      header <- hh
//      body <- bb
//    } yield (header,body)
//  }

}
