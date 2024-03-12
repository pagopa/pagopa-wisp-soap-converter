package it.gov.pagopa.tests

import akka.actor.ActorSystem
import com.typesafe.config.Config
import it.gov.pagopa.ActorProps
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.util.web.NodoRoute
import it.gov.pagopa.common.util.xml.XmlUtil.StringBase64Binary
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.common.util.{AppLogger, DDataChecks, StringUtils}
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.config.{PaymentServiceProvider, PaymentType}
import it.gov.pagopa.tests.testutil.{SpecsUtils, TestDData, TestItems}
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.ExecutionContext
import scala.util.Try

class ChecksUnitTests  extends AnyFlatSpec with should.Matchers {

  val log = mock[AppLogger]

  "route errors" should "ok" in {
    val settings = mock[ActorSystem.Settings]
    val config = mock[Config]
    val as = mock[ActorSystem]
    when(as.settings).thenReturn(settings)
    when(settings.config).thenReturn(config)
    when(config.getInt("config.http.server-request-timeout")).thenReturn(10)
    val ec = mock[ExecutionContext]
    val log = mock[AppLogger]
    val nr = new NodoRoute(as,Map(),"",200,mock[ActorProps])(ec,log)
    val timeout = nr.akkaHttpTimeout("sid")
    val enc = nr.akkaErrorEncoding("sid","UTF-8")
    assert(timeout.status.isFailure())
    assert(enc.status.isFailure())
  }

  "getConfigurationKeys" should "ok" in {
    val t = Try(DDataChecks.getConfigurationKeys(TestDData.ddataMap,"aaaa","bbb"))
    assert(t.isFailure)
  }

  "checkPspCanaleTipoVersamento" should "ok" in {
    val t = DDataChecks.checkPspCanaleTipoVersamento(log,TestDData.ddataMap,TestDData.ddataMap.psps.head._2,TestDData.ddataMap.channels.head._2,
      None)
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_AUTORIZZAZIONE)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Configurazione psp-canale non corretta")
  }
  "checkPspCanaleTipoVersamento 2" should "ok" in {
    val t = DDataChecks.checkPspCanaleTipoVersamento(log,TestDData.ddataMap,TestDData.ddataMap.psps(TestItems.PSP),TestDData.ddataMap.channels(TestItems.canale),
      Some(new PaymentType("DDD",None)))
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_AUTORIZZAZIONE)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Configurazione psp-canale-tipoVersamento non corretta")
  }


  "checkPaStazionePa" should "ok" in {
    val t = DDataChecks.checkPaStazionePa(
      log,
      TestDData.ddataMap,
      TestItems.PA,"0000000")
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Configurazione pa-progressivo stazione non corretta")
  }
  "checkPaStazionePa 2" should "ok" in {
    val t = DDataChecks.checkPaStazionePa(
      log,
      TestDData.ddataMap,
      TestItems.PA,"007000000000000")
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_STAZIONE_INT_PA_DISABILITATA)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Stazione disabilitata.")
  }
  "checkPaStazionePa 3" should "ok" in {
    val t = DDataChecks.checkPaStazionePa(
      log,
      TestDData.ddataMap,
      TestItems.PA,"008000000000000")
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Intermediario dominio disabilitato.")
  }
  "checkPaStazionePa 4" should "ok" in {
    val t = DDataChecks.checkPaStazionePa(
      log,
      TestDData.ddataMap,
      TestItems.PA,"009000000000000")
    assert(t.isFailure)
    assert(t.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA)
    assert(t.failed.get.asInstanceOf[DigitPaException].message == "Configurazione pa-progressivo stazione non corretta")
  }

  "StringUtils" should "getStringDecodedByString" in {
    assert("test" == StringUtils.getStringDecodedByString(StringBase64Binary.encodeBase64ToString("test".getBytes),true).get)
  }

  "XSD" should "success" in {
    val p = SpecsUtils.loadTestXML(s"/requests/rpt.xml")
    assert(XsdValid.checkOnly(p,XmlEnum.RPT_PAGINF,true).isFailure)
  }

  "PA" should "success" in {
    val t1 = DDataChecks.checkPA(log,TestDData.ddataMap,TestItems.PA)
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkPA(log,TestDData.ddataMap,TestItems.PA_DISABLED)
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_DOMINIO_DISABILITATO)
    val t3 = DDataChecks.checkPA(log,TestDData.ddataMap,"#")
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
  }
  "PSP" should "success" in {
    val t1 = DDataChecks.checkPsp(log,TestDData.ddataMap,TestItems.PSP)
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkPsp(log,TestDData.ddataMap,TestItems.PSP_DISABLED)
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_PSP_DISABILITATO)
    val t3 = DDataChecks.checkPsp(log,TestDData.ddataMap,"#")
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_PSP_SCONOSCIUTO)
  }
  "INT_PSP" should "success" in {
    val t1 = DDataChecks.checkIntermediarioPSP(log,TestDData.ddataMap,TestItems.intPSP)
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkIntermediarioPSP(log,TestDData.ddataMap,TestItems.intPSP_DISABLED)
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_DISABILITATO)
    val t3 = DDataChecks.checkIntermediarioPSP(log,TestDData.ddataMap,"#")
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_SCONOSCIUTO)
  }
  "INT_PA" should "success" in {
    val t1 = DDataChecks.checkIntermediarioPA(log,TestDData.ddataMap,TestItems.testIntPA)
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkIntermediarioPA(log,TestDData.ddataMap,TestItems.testIntPA_DISABLED)
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
    val t3 = DDataChecks.checkIntermediarioPA(log,TestDData.ddataMap,"#")
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_SCONOSCIUTO)
  }
  "CANALE" should "success" in {
    val t1 = DDataChecks.checkCanale(log,TestDData.ddataMap,TestItems.canale,Some(TestItems.canalePwd))
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkCanale(log,TestDData.ddataMap,TestItems.canale_DISABLED,Some(""))
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_CANALE_DISABILITATO)
    val t3 = DDataChecks.checkCanale(log,TestDData.ddataMap,"#",None)
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_CANALE_SCONOSCIUTO)
    val t4 = DDataChecks.checkCanale(log,TestDData.ddataMap,TestItems.canale,Some("wrongpass"))
    assert(t4.isFailure)
    assert(t4.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_AUTENTICAZIONE)
  }
  "STAZIONE" should "success" in {
    val t1 = DDataChecks.checkStazione(log,TestDData.ddataMap,TestItems.stazione,Some(TestItems.stazionePwd))
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkStazione(log,TestDData.ddataMap,TestItems.stazione_DISABLED,Some(""))
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_STAZIONE_INT_PA_DISABILITATA)
    val t3 = DDataChecks.checkStazione(log,TestDData.ddataMap,"#",None)
    assert(t3.isFailure)
    assert(t3.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA)
    val t4 = DDataChecks.checkStazione(log,TestDData.ddataMap,TestItems.stazione,Some("wrongpass"))
    assert(t4.isFailure)
    assert(t4.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_AUTENTICAZIONE)
  }
  "ALTRI CHECK" should "success" in {
    val t1 = DDataChecks.checkTipoVersamento(log,TestDData.ddataMap,"PO")
    assert(t1.isSuccess)
    val t2 = DDataChecks.checkTipoVersamento(log,TestDData.ddataMap,"wrong")
    assert(t2.isFailure)
    assert(t2.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_TIPO_VERSAMENTO_SCONOSCIUTO)
    val t3 = DDataChecks.checkIban(log,TestDData.ddataMap,"77777777777","IT96R0123454321000000012345")
    assert(t3.isSuccess)
    val t4 = DDataChecks.checkIban(log,TestDData.ddataMap,"wrong","")
    assert(t4.isFailure)
    assert(t4.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_IBAN_NON_CENSITO)
  }

}
