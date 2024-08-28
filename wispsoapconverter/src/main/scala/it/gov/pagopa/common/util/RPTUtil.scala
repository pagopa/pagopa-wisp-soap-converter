package it.gov.pagopa.common.util

import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.xml.XmlUtil.StringBase64Binary
import scalaxb.Base64Binary
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, IntestazionePPT}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import scala.util.{Success, Try}
import scala.xml.XML

object RPTUtil {

  def getAdapterEcommerceUri(uri: String, req:SoapRequest): String = {
//    s"${uri}${getUniqueKey(req, int)}"
    uri.replaceAll("REPLACE", req.sessionId)
  }

  def getRptByBase64Binary(rptEncoded: Base64Binary): Try[CtRichiestaPagamentoTelematico] = {
    Success(scalaxb.fromXML[CtRichiestaPagamentoTelematico](XML.loadString(StringBase64Binary.decodeBase64(rptEncoded))))
  }

}
