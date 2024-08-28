package it.gov.pagopa.tests

import akka.actor.Props
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.NodoInviaRPTActorPerRequest
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message.{SoapRequest, SoapResponse}
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.RPTKey
import it.gov.pagopa.common.util.xml.XmlUtil
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import it.gov.pagopa.tests.testutil.TestItems
import scalaxbmodel.nodoperpa.{POValue, TipoElementoListaRPT}
import scalaxbmodel.paginf.{CtDatiVersamentoRPT, CtRichiestaPagamentoTelematico, CtSoggettoPagatoreType, CtSoggettoVersante}

import java.time.Instant
import java.util.UUID
import scala.util.Try
import scala.xml.XML

//@org.scalatest.Ignore
class FakeInviaRPTActor(override val cosmosRepository: CosmosRepository, override val actorProps: ActorProps) extends NodoInviaRPTActorPerRequest(cosmosRepository, actorProps) {
  override def receive: Receive = {
    case ("checkEmail", ctRPT: CtRichiestaPagamentoTelematico) =>
      sender() ! this.checkEmail(ctRPT)
    case ("checkCanaleModello", data: ConfigData, idCanale: String) =>
      sender() ! this.checkCanaleModello(data, idCanale)
    case ("checkRptNumbers", maxNumRptInCart: Int, rpts: Seq[TipoElementoListaRPT]) =>
      sender() ! this.checkRptNumbers(maxNumRptInCart, rpts)
    case ("checkDuplicatoNelloStessoCarrello", rpts: Seq[TipoElementoListaRPT]) =>
      sender() ! this.checkDuplicatoNelloStessoCarrello(rpts)
    case ("codiceVersantePagatoreNonCoerente", rpts: Seq[TipoElementoListaRPT]) =>
      sender() ! this.codiceVersantePagatoreNonCoerente(rpts)
    case ("ibanChecks", data: ConfigData, ct: CtDatiVersamentoRPT, idDominio: String) =>
      sender() ! this.ibanChecks(data, ct, idDominio)
    case ("errorRpt", sessionid: String) =>
      req = SoapRequest(sessionid, "", "", "", "", Instant.now(), null, false, None)
      replyTo = sender()
      this.actorError(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
    case ("recoverGenericError", sessionid: String) =>
      req = SoapRequest(sessionid, "", "", "", "", Instant.now(), null, false, None)
      val replyto = sender()
      val x = this.recoverGenericError.apply(new IllegalArgumentException("test error"))
      x.map(s => replyto ! s)
    case ("recoverFuture", sessionid: String, th: Throwable) =>
      req = SoapRequest(sessionid, "", "", "", "", Instant.now(), null, false, None)
      val replyto = sender()
      val x = this.recoverFuture.apply(th)
      x.map(s => replyto ! s)
  }
}

class InviaRPTUnitTests() extends BaseUnitTest {

  val fakeInviaRPTActor = system.actorOf(Props.create(classOf[FakeInviaRPTActor], cosmosRepository, props), "fakeInviaRPTActor")

  "FakeInviaRPTActor" must {
    "recoverGenericError" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(fakeInviaRPTActor, ("recoverGenericError", sessionid)).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_SYSTEM_ERROR.toString))
    }
    "recoverFuture IllegalArgumentException" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(fakeInviaRPTActor, ("recoverFuture", sessionid, new IllegalArgumentException("test error"))).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_SYSTEM_ERROR.toString))
    }
    "recoverFuture DigitPaException" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(fakeInviaRPTActor, ("recoverFuture", sessionid, DigitPaException(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO))).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO.toString))
    }
    "recoverFuture RptFaultBeanException" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(fakeInviaRPTActor, ("recoverFuture", sessionid,
        RptFaultBeanException(
          DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO,
          workflowErrorCode = Some(WorkflowExceptionErrorCodes.RPT_ERRORE_INVIO_CD),
          rptKey = Some(RPTKey("", "", ""))
        ))).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_SYSTEM_ERROR.toString))
    }
  }

  "validazione sintattica" must {
    "PPT_SINTASSI_EXTRAXSD" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        stationPwd = Some("passwordMaxLength"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD.faultString))
        }
      )
    }
  }

  "validazione semantica" must {
    "PPT_SEMANTICA" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        iuvRpt = Some(s"${RandomStringUtils.randomNumeric(15)}"),
        ccpRpt = Some(s"${RandomStringUtils.randomNumeric(15)}"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_SEMANTICA.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_SEMANTICA.faultString))
          assert(r.fault.get.description.isDefined)
          assert(r.fault.get.description.get.equals("Dati di intestazione non coerenti con RPT"))
        }
      )
    }
    "PPT_PSP_SCONOSCIUTO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        psp = Some("pspSconosciuto"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_PSP_SCONOSCIUTO.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_PSP_SCONOSCIUTO.faultString))

        }
      )
    }
    "PPT_INTERMEDIARIO_PA_SCONOSCIUTO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        brokerPa = Some("intPASconosciuto"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_SCONOSCIUTO.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_SCONOSCIUTO.faultString))

        }
      )
    }
    "PPT_DOMINIO_SCONOSCIUTO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        pa = Some("paSconosciuta"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO.faultString))
        }
      )
    }
    "PPT_CANALE_SCONOSCIUTO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some("canaleSconosciuto"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_CANALE_SCONOSCIUTO.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_CANALE_SCONOSCIUTO.faultString))

        }
      )
    }
    "PPT_AUTENTICAZIONE" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        stationPwd = Some("passwordWrong"),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
          assert(r.fault.nonEmpty)
          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_AUTENTICAZIONE.faultCode))
          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_AUTENTICAZIONE.faultString))
        }
      )
    }
    //    "PPT_SINTASSI_XSD" in {
    //      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
    //      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
    //      nodoInviaRpt(
    //        iuv,
    //        ccp,
    //        stationPwd = Some("passwordWrong"),
    //        responseAssert = (r) => {
    //          assert(r.esito == "KO", "outcome in res")
    //          assert(r.fault.nonEmpty)
    //          assert(r.fault.get.faultCode.equals(DigitPaErrorCodes.PPT_SINTASSI_XSD.faultCode))
    //          assert(r.fault.get.faultString.equals(DigitPaErrorCodes.PPT_SINTASSI_XSD.faultString))
    //        }
    //      )
    //    }
    "checkTipoVersamentoDatiVersamento" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        tipoVersamento = Some("PO"),
        versamenti = 2,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il tipo di versamento indicato comporta la valorizzazione di un unico elemento datiSingoloVersamento (numero attuale elementi: 2 )")
        }
      )
    }
    "ibanAddebitoCheck" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        tipoVersamento = Some("AD"),
        ibanAddebito = Some(""),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il tipo di versamento indicato comporta la valorizzazione dell'iban di addebito")
        }
      )
    }
    "singleCheck" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        tipoVersamento = Some("OBEP"),
        versamenti = 2,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il tipo di versamento indicato comporta la valorizzazione di un unico elemento datiSingoloVersamento, ctDatiVersamentoRPT.tipoVersamento: [OBEP], ctDatiVersamentoRPT.datiSingoloVersamento.length: [2]")
        }
      )
    }
    "importoCheck" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        versamenti = 2,
        importiVersamenti = Seq(BigDecimal.apply(10), BigDecimal.apply(1)),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "La somma degli importoSingoloVersamento deve coincidere con l'importoTotaleDaVersare")
        }
      )
    }
    "dataCheck 1" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        dataEsecuzionePagamento = "2017-09-05",
        dataOraMessaggioRichiesta = "2017-09-06",
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il campo dataEsecuzionePagamento deve avere un valore uguale o maggiore del campo dataOraMessaggioRichiesta")
        }
      )
    }
    "dataCheck 2" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        dataEsecuzionePagamento = "2018-09-05",
        dataOraMessaggioRichiesta = "2017-09-06",
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il campo dataEsecuzionePagamento deve avere un valore non eccedente i 30 giorni dal valore del campo dataOraMessaggioRichiesta")
        }
      )
    }
    "checkPspAbilitatoAlBollo" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        bollo = true,
        psp = Some(TestItems.PSP_NO_BOLLO),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_CANALE_SERVIZIO_NONATTIVO")
          assert(r.fault.get.description.get == "PSP non abilitato alla ricezione della marca da bollo digitale")
        }
      )
    }
    "ccpNonNACheck" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        psp = Some(TestItems.PSP),
        tipoVersamento = Some("PO"),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
          assert(r.fault.get.description.get == "Il tipo di versamento indicato non ammette la valorizzazione del codiceContestoPagamento con valore n/a")
        }
      )
    }

    "checkPluginRedirectCanale" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediatoNoPlugin),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore generico.")
        }
      )
    }

    "checkUrlCanale Protocollo" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediato),
        modifyData = (data) => {
          val ch = data.channels(TestItems.canaleImmediato)
          data.copy(channels = data.channels + (TestItems.canaleImmediato -> ch.copy(redirect = ch.redirect.copy(protocol = None))))
        },
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore Protocollo di redirezione Canale non valorizzato")
        }
      )
    }
    "checkUrlCanale Protocollo 2" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediato),
        modifyData = (data) => {
          val ch = data.channels(TestItems.canaleImmediato)
          data.copy(channels = data.channels + (TestItems.canaleImmediato -> ch.copy(redirect = ch.redirect.copy(protocol = Some("FTP")))))
        },
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore Protocollo di redirezione Canale non valorizzato correttamente, redirectProtocollo: [FTP]")
        }
      )
    }
    "checkUrlCanale ip" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediato),
        modifyData = (data) => {
          val ch = data.channels(TestItems.canaleImmediato)
          data.copy(channels = data.channels + (TestItems.canaleImmediato -> ch.copy(redirect = ch.redirect.copy(ip = None))))
        },
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore Ip di redirezione Canale non valorizzato")
        }
      )
    }
    "checkUrlCanale porta" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediato),
        modifyData = (data) => {
          val ch = data.channels(TestItems.canaleImmediato)
          data.copy(channels = data.channels + (TestItems.canaleImmediato -> ch.copy(redirect = ch.redirect.copy(port = None))))
        },
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore Porta di redirezione Canale non valorizzata")
        }
      )
    }
    "checkUrlCanale path" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"n/a"
      nodoInviaRpt(
        iuv,
        ccp,
        canale = Some(TestItems.canaleImmediato),
        modifyData = (data) => {
          val ch = data.channels(TestItems.canaleImmediato)
          data.copy(channels = data.channels + (TestItems.canaleImmediato -> ch.copy(redirect = ch.redirect.copy(path = None))))
        },
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SYSTEM_ERROR")
          assert(r.fault.get.description.get == "Errore Path di redirezione Canale non valorizzato")
        }
      )
    }
    "CommonRptCheck checkEmail" in {
      val rpt = new CtRichiestaPagamentoTelematico(
        null, null, null, null, null, Some(
          new CtSoggettoVersante(null, null, null, null, null, null, null, null, Some("versante@gmail.com"))
        ), null, null, null
      )
      val res = askActorType(fakeInviaRPTActor, ("checkEmail", rpt)).asInstanceOf[Try[String]]
      assert(res.isSuccess)
      assert(res.get == "versante@gmail.com")
    }
    "CommonRptCheck checkEmail 2" in {
      val rpt = new CtRichiestaPagamentoTelematico(
        null, null, null, null, null, Some(
          new CtSoggettoVersante(null, null, null, null, null, null, null, null, None)
        ),
        new CtSoggettoPagatoreType(null, null, null, null, null, null, null, null, Some("pagatore@gmail.com"))
        , null, null
      )
      val res = askActorType(fakeInviaRPTActor, ("checkEmail", rpt)).asInstanceOf[Try[String]]
      assert(res.isSuccess)
      assert(res.get == "pagatore@gmail.com")
    }

    "CommonRptCheck checkCanaleModello" in {
      val res = askActorType(fakeInviaRPTActor, ("checkCanaleModello", TestItems.ddataMap, TestItems.canale)).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
    }
    "CommonRptCheck checkRptNumbers" in {
      val res = askActorType(fakeInviaRPTActor, ("checkRptNumbers", 1, Seq(null, null))).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
    }
    "CommonRptCheck checkRptNumbers2" in {
      val res = askActorType(fakeInviaRPTActor, ("checkRptNumbers", 1, Seq())).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
    }
    "CommonRptCheck checkDuplicatoNelloStessoCarrello" in {
      val lista = Seq(
        new TipoElementoListaRPT("1", "2", "3", None, null),
        new TipoElementoListaRPT("1", "2", "3", None, null)
      )
      val res = askActorType(fakeInviaRPTActor, ("checkDuplicatoNelloStessoCarrello", lista)).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
      assert(res.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_SEMANTICA)
      assert(res.failed.get.asInstanceOf[DigitPaException].message == "Rpt duplicata nello stesso carrello")
    }
    "CommonRptCheck codiceVersantePagatoreNonCoerente" in {
      val rpt1 = payload("/requests/rpt")
        .replace("{dataOraMessaggioRichiesta}", "2020-10-10")
        .replace("{dataEsecuzionePagamento}", "2020-10-10")
        .replace("{amount}", "10.00")
        .replace("{tipoVersamento}", "PO")
        .replace("{versamenti}", "")
      val rpt2 = payload("/requests/rpt")
        .replace("{dataOraMessaggioRichiesta}", "2020-10-10")
        .replace("{dataEsecuzionePagamento}", "2020-10-10")
        .replace("{amount}", "10.00")
        .replace("{tipoVersamento}", "PO")
        .replace("{versamenti}", "")
        .replace("TTTTTT11T11T123T", "aaa")
      val lista = Seq(
        new TipoElementoListaRPT("1", "2", "3", None, XmlUtil.StringBase64Binary.encodeBase64(rpt1.getBytes)),
        new TipoElementoListaRPT("1", "2", "3", None, XmlUtil.StringBase64Binary.encodeBase64(rpt2.getBytes))
      )
      val res = askActorType(fakeInviaRPTActor, ("codiceVersantePagatoreNonCoerente", lista)).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
      assert(res.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_SEMANTICA)
      assert(res.failed.get.asInstanceOf[DigitPaException].message == "codice pagatore non coerente")
    }
    "ValidateRPT ibanChecks" in {

      val rpt1 = payload("/requests/rptBollo")
        .replace("{dataOraMessaggioRichiesta}", "2020-10-10")
        .replace("{dataEsecuzionePagamento}", "2020-10-10")
        .replace("{amount}", "10.00")
        .replace("{tipoVersamento}", "PO")
        .replace("{amountBollo}", "0.01")
        .replace("{amountVersamento1}", "0.01")
        .replace("{amount}", "0.01")
      val rpt = scalaxb.fromXML[CtRichiestaPagamentoTelematico](XML.loadString(rpt1))
      val res = askActorType(fakeInviaRPTActor, ("ibanChecks", TestItems.ddataMap, rpt.datiVersamento, TestItems.PA)).asInstanceOf[Try[Boolean]]
      assert(res.isSuccess)
    }
    "ValidateRPT ibanChecks 2" in {

      val rpt1 = payload("/requests/rptBollo")
        .replace("{dataOraMessaggioRichiesta}", "2020-10-10")
        .replace("{dataEsecuzionePagamento}", "2020-10-10")
        .replace("{amount}", "10.00")
        .replace("{tipoVersamento}", "PO")
        .replace("{amountBollo}", "0.01")
        .replace("{amountVersamento1}", "0.01")
        .replaceAll("<datiMarcaBolloDigitale>[\\s\\S]*</datiMarcaBolloDigitale>", "")
      val rpt = scalaxb.fromXML[CtRichiestaPagamentoTelematico](XML.loadString(rpt1))
      val res = askActorType(fakeInviaRPTActor, ("ibanChecks", TestItems.ddataMap, rpt.datiVersamento, TestItems.PA)).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
      assert(res.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_SEMANTICA)
      assert(res.failed.get.asInstanceOf[DigitPaException].message == "IBAN accredito o marca da bollo non presenti")
    }
    "ValidateRPT ibanChecks 3" in {

      val rpt1 = payload("/requests/rptBollo")
        .replace("{dataOraMessaggioRichiesta}", "2020-10-10")
        .replace("{dataEsecuzionePagamento}", "2020-10-10")
        .replace("{amount}", "10.00")
        .replace("{tipoVersamento}", "PO")
        .replace("{amountBollo}", "0.01")
        .replace("{amountVersamento1}", "0.01")
        .replaceAll("<datiSpecificiRiscossione>9/tipodovuto_6</datiSpecificiRiscossione>[\\s\\S]", "<datiSpecificiRiscossione>9/tipodovuto_6</datiSpecificiRiscossione><datiMarcaBolloDigitale>\n                <tipoBollo>01</tipoBollo>\n                <hashDocumento>YWVvbGlhbQ==</hashDocumento>\n                <provinciaResidenza>MI</provinciaResidenza>\n            </datiMarcaBolloDigitale>")
      val rpt = scalaxb.fromXML[CtRichiestaPagamentoTelematico](XML.loadString(rpt1))
      val res = askActorType(fakeInviaRPTActor, ("ibanChecks", TestItems.ddataMap, rpt.datiVersamento, TestItems.PA)).asInstanceOf[Try[Boolean]]
      assert(res.isFailure)
      assert(res.failed.get.asInstanceOf[DigitPaException].code == DigitPaErrorCodes.PPT_SEMANTICA)
      assert(res.failed.get.asInstanceOf[DigitPaException].message == "La presenza di una marca da bollo digitale comporta la non valorizzazione dell'elemento ibanAccredito, iban: [IT96R0123454321000000012345]")
    }

    "rpt actor error" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(fakeInviaRPTActor, ("errorRpt", sessionid)).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO.toString))
    }
  }


  "OK" must {
    "OK" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        responseAssert = (r) => {
          assert(r.esito == "OK", "outcome in res")
          assert(r.fault.isEmpty)
        }
      )
    }
    "OK appIO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        brokerPa = Some(TestItems.testIntPA),
        canale = Some(TestItems.canaleAgid),
        psp = Some(TestItems.PSPAgid),
        brokerPsp = Some(TestItems.intPSPAgid),
        tipoVersamento = Some(POValue.toString),
        responseAssert = (r) => {
          assert(r.esito == "OK", "outcome in res")
          assert(r.fault.isEmpty)
          assert(r.redirect.get == 1)
          assert(r.url.isDefined)
          assert(r.url.get.contains(s"?idSession="))
        }
      )
    }
    "OK canale IMMEDIATO" in {
      val iuv = s"${RandomStringUtils.randomNumeric(15)}"
      val ccp = s"${RandomStringUtils.randomNumeric(15)}"
      nodoInviaRpt(
        iuv,
        ccp,
        brokerPa = Some(TestItems.testIntPA),
        canale = Some(TestItems.canaleImmediato),
        psp = Some(TestItems.PSP),
        brokerPsp = Some(TestItems.intPSP),
        tipoVersamento = Some(POValue.toString),
        responseAssert = (r) => {
          assert(r.esito == "OK", "outcome in res")
          assert(r.fault.isEmpty)
          assert(r.redirect.get == 1)
          assert(r.url.isDefined)
          assert(r.url.get.contains(s"?idSession="))
        }
      )
    }

  }
}
