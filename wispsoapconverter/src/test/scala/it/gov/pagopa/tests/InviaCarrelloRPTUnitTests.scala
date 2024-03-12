package it.gov.pagopa.tests

import akka.actor.{Actor, Props}
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.flow.CarrelloFlow
import it.gov.pagopa.actors.response.NodoInviaCarrelloRPTResponse
import it.gov.pagopa.common.actor.{NodoLogging, PerRequestActor}
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message.{SoapRequest, SoapResponse}
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.xml.XmlUtil
import it.gov.pagopa.common.util.{CommonRptCheck, RPTKey, Util, ValidateRpt}
import it.gov.pagopa.config.{BrokerCreditorInstitution, Station}
import it.gov.pagopa.exception.{CarrelloRptFaultBeanException, RptFaultBeanException}
import it.gov.pagopa.tests.testutil.TestItems
import org.mockito.MockitoSugar.{mock, when}
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, NodoInviaCarrelloRPT, TipoElementoListaRPT, TipoListaRPT}

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime
import java.util.UUID
import scala.util.Try
//@org.scalatest.Ignore

case class ValidationActorCarrello() extends Actor with NodoInviaCarrelloRPTResponse with CommonRptCheck with ValidateRpt with NodoLogging {
  override def receive: Receive = {
    case ("errorCarrello",sid:String)=>
      this.actorError(sender(),SoapRequest(sid,"","","","",LocalDateTime.now(),null,false,None),None,DigitPaErrorCodes.PPT_SYSTEM_ERROR,None,None,None)
  }
}

case class FakeCarrelloFlowActor(actorProps: ActorProps) extends PerRequestActor with CarrelloFlow{
  override def receive: Receive = {
    case ("validCarrello")=>
      val data = mock[ConfigData]
      when(data.psps).thenThrow(new IllegalArgumentException("test error"))
      sender() ! this.validCarrello(data,1,Seq(),new IntestazioneCarrelloPPT("","","",Map()),new NodoInviaCarrelloRPT("","","","",null,None,None,None),false,"","","")
    case ("validCarrelloMultibeneficiario")=>
      val data = mock[ConfigData]
      when(data.creditorInstitutions).thenThrow(new IllegalArgumentException("test error"))
      sender() ! this.validCarrelloMultibeneficiario(data,1,
        new IntestazioneCarrelloPPT("","","AAAAAAAAAAAFFFFFFFFFFFFFFFFFF-11111",Map()),
        new NodoInviaCarrelloRPT("","","","",new TipoListaRPT(Nil),None,None,None))
    case ("parseRpts",rpt:Array[Byte])=>
      val data = mock[ConfigData]
      when(data.creditorInstitutions).thenThrow(new IllegalArgumentException("test error"))
      sender() ! this.parseRpts("",true,
        Seq(new TipoElementoListaRPT("1","2","3",None,XmlUtil.StringBase64Binary.encodeBase64(rpt))),
      "",Seq(),true)
  }

  override val cosmosRepository: CosmosRepository = mock[CosmosRepository]
  override def actorError(e: DigitPaException): Unit = ???
}

class InviaCarrelloRPTUnitTests() extends BaseUnitTest {
  val validationActor=system.actorOf(Props[ValidationActorCarrello],"name")
  val fakeCarrelloFlow=system.actorOf(Props.create(classOf[FakeCarrelloFlowActor],props),"fakeCarrelloFlow")
  "validations" must {
    "carrello actor error" in {
      val sessionid = UUID.randomUUID().toString
      val res = askActorType(validationActor,("errorCarrello",sessionid)).asInstanceOf[SoapResponse]
      assert(res.sessionId == sessionid)
      assert(res.payload.contains(DigitPaErrorCodes.PPT_SYSTEM_ERROR.toString))
    }
    "carrello valid carrello error" in {
      val res = askActorType(fakeCarrelloFlow,"validCarrello").asInstanceOf[Try[(BrokerCreditorInstitution, Station)]]
      assert(res.failed.get.asInstanceOf[CarrelloRptFaultBeanException].digitPaException.code == DigitPaErrorCodes.PPT_SYSTEM_ERROR)
    }
    "validCarrelloMultibeneficiario" in {
      val res = askActorType(fakeCarrelloFlow,"validCarrelloMultibeneficiario").asInstanceOf[Try[(BrokerCreditorInstitution, Station)]]
      assert(res.failed.get.asInstanceOf[CarrelloRptFaultBeanException].digitPaException.code == DigitPaErrorCodes.PPT_SYSTEM_ERROR)
    }
    "parseRpts" in {
      val res = askActorType(fakeCarrelloFlow,("parseRpts","aaaa".getBytes(StandardCharsets.UTF_8))).asInstanceOf[Try[(BrokerCreditorInstitution, Station)]]
      assert(res.failed.get.asInstanceOf[CarrelloRptFaultBeanException].digitPaException.code == DigitPaErrorCodes.PPT_SINTASSI_XSD)
    }
    "parseRpts2" in {
      val res = askActorType(fakeCarrelloFlow,("parseRpts","èà+èà+@".getBytes(StandardCharsets.ISO_8859_1))).asInstanceOf[Try[(BrokerCreditorInstitution, Station)]]
      assert(res.failed.get.asInstanceOf[CarrelloRptFaultBeanException].digitPaException.code == DigitPaErrorCodes.PPT_SINTASSI_XSD)
    }
  }


  "inviaCarrelloRPT" must {
    "PPT_SINTASSI_EXTRAXSD" in {
      val noticeNumber = s"002${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s""

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SINTASSI_EXTRAXSD")
        }
      )
    }
    "PPT_CANALE_DISABILITATO" in {
      val noticeNumber = s"002${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        channel=TestItems.canale_DISABLED,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_CANALE_DISABILITATO")
        }
      )
    }
    "PPT_CANALE_SCONOSCIUTO" in {
      val noticeNumber = s"002${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        channel="sconosciuto",
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_CANALE_SCONOSCIUTO")
        }
      )
    }
    "ok" in {
      val noticeNumber = s"002${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        responseAssert = (r) => {
          assert(r.fault.isEmpty)
        }
      )

    }
    "ok multibeneficiario" in {
      val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )
      val psp = TestItems.PSPAgid
      val brokerPsp = TestItems.intPSPAgid
      val channel = TestItems.canaleAgid

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        psp=psp,
        brokerPsp = brokerPsp,
        channel = channel,
        multibeneficiario = true,
        responseAssert = (r) => {
          assert(r.fault.isEmpty)
        }
      )
    }
    "ko multibeneficiario PPT_MULTI_BENEFICIARIO" in {
      val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-0"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )
      val psp = TestItems.PSPAgid
      val brokerPsp = TestItems.intPSPAgid
      val channel = TestItems.canaleAgid

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        psp=psp,
        brokerPsp = brokerPsp,
        channel = channel,
        multibeneficiario = true,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_MULTI_BENEFICIARIO")
        }
      )
    }
    "ko multibeneficiario no agid" in {
      val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
      val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
      val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

      val rpts = Seq(
        RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
        RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
      )
      val psp = TestItems.PSP
      val brokerPsp = TestItems.intPSP
      val channel = TestItems.canaleDifferito

      val (resp, sessionId) = nodoInviaCarrelloRpt(
        idCarrello,
        rpts,
        psp=psp,
        brokerPsp = brokerPsp,
        channel = channel,
        multibeneficiario = true,
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == "PPT_SEMANTICA")
        }
      )
    }
  }
  "ok multibeneficiario staz v1" in {
    val noticeNumber = s"002${RandomStringUtils.randomNumeric(15)}"
    val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
    val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

    val rpts = Seq(
      RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
      RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
    )
    val psp = TestItems.PSPAgid
    val brokerPsp = TestItems.intPSPAgid
    val channel = TestItems.canaleAgid

    val (resp, sessionId) = nodoInviaCarrelloRpt(
      idCarrello,
      rpts,
      psp=psp,
      brokerPsp = brokerPsp,
      channel = channel,
      multibeneficiario = true,
      responseAssert = (r) => {
        assert(r.fault.isDefined)
        assert(r.fault.get.faultCode == "PPT_MULTI_BENEFICIARIO")
      }
    )
  }
  "ok multibeneficiario 3 rpt" in {
    val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
    val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
    val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"
    val rpts = Seq(
      RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
      RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10",
      RPTKey(TestItems.PA_2, iuv, idCarrello) -> "2020-10-10"
    )
    val psp = TestItems.PSPAgid
    val brokerPsp = TestItems.intPSPAgid
    val channel = TestItems.canaleAgid

    val (resp, sessionId) = nodoInviaCarrelloRpt(
      idCarrello,
      rpts,
      psp=psp,
      brokerPsp = brokerPsp,
      channel = channel,
      multibeneficiario = true,
      responseAssert = (r) => {
        assert(r.fault.isDefined)
        assert(r.fault.get.faultCode == "PPT_MULTI_BENEFICIARIO")
      }
    )
  }
  "ok multibeneficiario wrong id carrello" in {
    val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
    val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
    val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

    val rpts = Seq(
      RPTKey(TestItems.PA_2, iuv, idCarrello) -> "2020-10-10",
      RPTKey("77777777777", iuv, idCarrello) -> "2020-10-10"
    )
    val psp = TestItems.PSPAgid
    val brokerPsp = TestItems.intPSPAgid
    val channel = TestItems.canaleAgid

    val (resp, sessionId) = nodoInviaCarrelloRpt(
      idCarrello,
      rpts,
      psp=psp,
      brokerPsp = brokerPsp,
      channel = channel,
      multibeneficiario = true,
      responseAssert = (r) => {
        assert(r.listaErroriRPT.isDefined)
        assert(r.listaErroriRPT.get.fault.head.faultCode == "PPT_MULTI_BENEFICIARIO")
      }
    )
  }
  "ok multibeneficiario iuv diversi in rpt" in {
    val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
    val noticeNumber2 = s"001${RandomStringUtils.randomNumeric(15)}"
    val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
    val (iuv2, _, _, _) = Util.getNoticeNumberData(noticeNumber2)
    val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"

    val rpts = Seq(
      RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
      RPTKey(TestItems.PA_2, iuv2, idCarrello) -> "2020-10-10"
    )
    val psp = TestItems.PSPAgid
    val brokerPsp = TestItems.intPSPAgid
    val channel = TestItems.canaleAgid

    val (resp, sessionId) = nodoInviaCarrelloRpt(
      idCarrello,
      rpts,
      psp=psp,
      brokerPsp = brokerPsp,
      channel = channel,
      multibeneficiario = true,
      responseAssert = (r) => {
        assert(r.listaErroriRPT.isDefined)
        assert(r.listaErroriRPT.get.fault.head.faultCode == "PPT_MULTI_BENEFICIARIO")
        assert(r.listaErroriRPT.get.fault.head.description.get == "Lo IUV non è identico per ogni RPT")
      }
    )
  }

//  "ok multibeneficiario date diversi in rpt" in {
//    val noticeNumber = s"001${RandomStringUtils.randomNumeric(15)}"
//    val (iuv, _, _, _) = Util.getNoticeNumberData(noticeNumber)
//    val idCarrello = s"${TestItems.PA}${noticeNumber}-00011"
//
//    val rpts = Seq(
//      RPTKey(TestItems.PA, iuv, idCarrello) -> "2020-10-10",
//      RPTKey(TestItems.PA_2, iuv, idCarrello) -> "2020-10-12"
//    )
//    val psp = TestItems.PSPAgid
//    val brokerPsp = TestItems.intPSPAgid
//    val channel = TestItems.canaleAgid
//
//    val (resp, sessionId) = nodoInviaCarrelloRpt(
//      idCarrello,
//      rpts,
//      psp=psp,
//      brokerPsp = brokerPsp,
//      channel = channel,
//      multibeneficiario = true,
//      responseAssert = (r) => {
//        assert(r.listaErroriRPT.isDefined)
//        assert(r.listaErroriRPT.get.fault.head.faultCode == "PPT_MULTI_BENEFICIARIO")
//        assert(r.listaErroriRPT.get.fault.head.description == "Il dato dataEsecuzionePagamento non è il medesimo per tutte le RPT")
//      }
//    )
//  }

}
