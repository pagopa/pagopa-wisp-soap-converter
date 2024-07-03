package it.gov.pagopa.tests

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.{NodoInviaCarrelloRPTActorPerRequest, NodoInviaRPTActorPerRequest}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.CosmosBuilder
import it.gov.pagopa.common.util.xml.XmlUtil
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.tests.testutil._
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import org.mockserver.integration.ClientAndServer
import org.scalatest.Assertion
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import scalaxbmodel.nodoperpa.{CP, NodoInviaCarrelloRPTRisposta, NodoInviaRPTRisposta}

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try


//@org.scalatest.Ignore
abstract class BaseUnitTest()
    extends TestKit({
      val mockServer = ClientAndServer.startClientAndServer()

      val config =
        s"""
        azurestorage-dispatcher {
            type = Dispatcher
            executor = "thread-pool-executor"
            thread-pool-executor {
               fixed-pool-size = 16
            }
            throughput = 1
        }
        config.http.connect-timeout = 1
        bundleTimeoutSeconds = 120
        bundle.checkUTF8 = false
        routing.useMetrics = false
        akka {
          loggers = ["akka.event.slf4j.Slf4jLogger"]
          loglevel = "INFO"
          logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
        }
        configScheduleMinutes=100000
        waitAsyncProcesses=true
        apiConfigCache{
          url="http://localhost:${mockServer.getPort}"
          keys="?keys=version,creditorInstitutions,creditorInstitutionBrokers,stations,creditorInstitutionStations,ibans,psps,pspBrokers,paymentTypes,pspChannelPaymentTypes,channels,plugins,configurations"
          subscriptionKey=1234567890
          timeout=30
        }
        adapterEcommerce{
          url="http://www.adapterEcommerce.pagopa.it?sessionId=REPLACE&idSession=REPLACE"
        }
    """
      val system = ActorSystem("testSystem", ConfigFactory.parseString(config))
      system.registerOnTermination(() => {
        mockServer.stop()
      })
      system.registerExtension(MockserverExtension)
      MockserverExtension(system).set(mockServer)
      system
    })
    with AnyWordSpecLike
    with ImplicitSender
    with should.Matchers {

  implicit val log: AppLogger = new AppLogger(Logging(system, getClass.getCanonicalName))
  implicit val ec: ExecutionContext = system.dispatcher

  val cosmosRepository = mock[CosmosRepository]
  when(cosmosRepository.save(any())).thenReturn(Future(200))

//  val reFunction = (request: ReRequest, log: AppLogger) => {
//    Future({
//      log.info(request.toString)
//    })
//  }

  val storageBuilder = mock[CosmosBuilder]
//  val tableClient = mock[TableClient]
//  val blobContainerClient = mock[BlobContainerClient]
//  val blobClient = mock[BlobClient]
//  when(blobContainerClient.getBlobClient(any())).thenReturn(blobClient)
//  when(storageBuilder.getClients(any())).thenReturn((tableClient,blobContainerClient))
//  when(storageBuilder.build()).thenCallRealMethod()

  val props = ActorProps(null, null, Map(), (_,_,_)=>Future.successful(()), "", TestItems.ddataMap)

  val singletesttimeout: FiniteDuration = 1000.seconds

  def payload(testfile: String): String = {
    SpecsUtils.loadTestXML(s"$testfile.xml")
  }

  def genericPayload(primitiva: String, iuv: String, ccp: String, date: Instant) = {
    val data = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dataora = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    val p = payload(s"/requests/$primitiva")
    val pp = if (p.contains("{ER}")) {
      val er = payload(s"/requests/ER")
        .replace("{iuv}", iuv)
        .replace("{ccp}", ccp)
        .replace("{station}", TestItems.stazioneOld)
        .replace("{pa}", TestItems.PA)
        .replace("{data}", data.format(date))
        .replace("{dataora}", dataora.format(date))
        .replace("{tiporevoca}", if (primitiva.contains("Revoca")) "<tipoRevoca>1</tipoRevoca>" else "")
      p.replace("{ER}", XmlUtil.StringBase64Binary.encodeBase64ToString(er.getBytes))
    } else if (p.contains("{RR}")) {
      val rr = payload(s"/requests/RR")
        .replace("{iuv}", iuv)
        .replace("{ccp}", ccp)
        .replace("{station}", TestItems.stazioneOld)
        .replace("{pa}", TestItems.PA)
        .replace("{data}", data.format(date))
        .replace("{dataora}", dataora.format(date))
        .replace("{tiporevoca}", if (primitiva.contains("Revoca")) "<tipoRevoca>1</tipoRevoca>" else "")
      p.replace("{RR}", XmlUtil.StringBase64Binary.encodeBase64ToString(rr.getBytes))
    } else {
      p
    }

    pp.replace("{psp}", TestItems.PSP)
      .replace("{brokerPsp}", TestItems.intPSP)
      .replace("{channel}", TestItems.canale)
      .replace("{channelPwd}", TestItems.canalePwd)
      .replace("{pa}", TestItems.PA)
      .replace("{brokerPa}", TestItems.testIntPA)
      .replace("{station}", TestItems.stazioneOld)
      .replace("{stationPwd}", TestItems.stazionePwd)
      .replace("{iuv}", iuv)
      .replace("{ccp}", ccp)
  }

  def nodoInviaRPTPayload(iuv: String,
                          ccp: String,
                          bollo: Boolean,
                          amount: BigDecimal = BigDecimal(10),
                          canale: Option[String] = None,
                          stazione: Option[String],
                          psp: Option[String] = None,
                          pa: Option[String] = None,
                          brokerPa: Option[String] = None,
                          brokerPsp: Option[String] = None,
                          stationPwd: Option[String] = None,
                          tipoVersamento: Option[String] = None,
                          iuvRpt: Option[String] = None,
                          ccpRpt: Option[String] = None,
                          ibanAddebito :Option[String] = None,
                          versamenti:Int = 1,
                          importiVersamenti:Seq[BigDecimal] = Seq(),
                          dataEsecuzionePagamento:String = "2017-09-06",
                          dataOraMessaggioRichiesta:String = "2017-09-06"
                         ) = {
    val rpt = (if (bollo) {
                 payload("/requests/rptBollo")
                   .replace("{amountBollo}", "0.01")
                   .replace("{amountVersamento1}", (amount -0.01).setScale(2).toString())
                   .replace("{amount}", (amount).setScale(2).toString())
               } else {
                  val vamount = amount/versamenti
                 payload("/requests/rpt").replace("{versamenti}",(1 to versamenti).map(v=>{
                   val importoversamento = Try(importiVersamenti(v)).getOrElse(vamount)
                   <datiSingoloVersamento>
                     <importoSingoloVersamento>{importoversamento.setScale(2).toString()}</importoSingoloVersamento>
                     <ibanAccredito>IT96R0123454321000000012345</ibanAccredito>
                     <causaleVersamento>Pagamento di prova {v}</causaleVersamento>
                     <datiSpecificiRiscossione>9/tipodovuto_7</datiSpecificiRiscossione>
                   </datiSingoloVersamento>.toString()
                 }).mkString("\n"))
               })
      .replace("{pa}", pa.getOrElse(TestItems.PA))
      .replace("{iuv}", iuvRpt.getOrElse(iuv))
      .replace("{ccp}", ccpRpt.getOrElse(ccp))
      .replace("{tipoVersamento}", tipoVersamento.getOrElse(CP.toString))
      .replace("{amount}", amount.setScale(2).toString())
      .replace("{dataEsecuzionePagamento}", dataEsecuzionePagamento)
      .replace("{dataOraMessaggioRichiesta}", dataOraMessaggioRichiesta)

    payload("/requests/nodoInviaRPT")
      .replace("{psp}", psp.getOrElse(TestItems.PSP))
      .replace("{brokerPsp}", brokerPsp.getOrElse(TestItems.intPSP))
      .replace("{channel}", canale.getOrElse(TestItems.canale))
      .replace("{pa}", pa.getOrElse(TestItems.PA))
      .replace("{brokerPa}", brokerPa.getOrElse(TestItems.testIntPA))
      .replace("{station}", stazione.getOrElse(TestItems.stazione))
      .replace("{stationPwd}", stationPwd.getOrElse(TestItems.stazionePwd))
      .replace("{iuv}", iuv)
      .replace("{ccp}", ccp)
      .replace("{rpt}", XmlUtil.StringBase64Binary.encodeBase64ToString(rpt.getBytes))
  }

  def nodoInviaCarrelloRptPayload(
      idCarrello: String,
      rpts: Seq[(RPTKey,String)],
      multibeneficiario: Boolean = false,
      brokerPa: String = TestItems.testIntPA,
      station: String = TestItems.stazioneOld,
      stationPassword: String = TestItems.stazionePwd,
      psp: String = TestItems.PSP,
      brokerPsp: String = TestItems.intPSP,
      channel: String = TestItems.canaleDifferito,
      amount:BigDecimal=BigDecimal.apply(10)
  ) = {
    val rptsCarrello = rpts.map(r => {
      val rr = payload("/requests/rpt").replace("{pa}", r._1.idDominio).replace("{iuv}", r._1.iuv).replace("{ccp}", r._1.ccp)
        .replace("{tipoVersamento}", "PO")
        .replace("{dataEsecuzionePagamento}", r._2)
        .replace("{dataOraMessaggioRichiesta}", r._2).replace("{versamenti}",
          <datiSingoloVersamento>
            <importoSingoloVersamento>{amount.setScale(2).toString()}</importoSingoloVersamento>
            <ibanAccredito>IT96R0123454321000000012345</ibanAccredito>
            <causaleVersamento>Pagamento di prova</causaleVersamento>
            <datiSpecificiRiscossione>9/tipodovuto_7</datiSpecificiRiscossione>
          </datiSingoloVersamento>.toString()
        )
        .replace("{amount}",amount.setScale(2).toString())
      s"""<elementoListaRPT>
        <identificativoDominio>${r._1.idDominio}</identificativoDominio>
        <identificativoUnivocoVersamento>${r._1.iuv}</identificativoUnivocoVersamento>
        <codiceContestoPagamento>${r._1.ccp}</codiceContestoPagamento>
        <tipoFirma/>
        <rpt>${XmlUtil.StringBase64Binary.encodeBase64ToString(rr.getBytes)}</rpt>
      </elementoListaRPT>"""
    })
    payload("/requests/nodoInviaCarrelloRPT")
      .replace("{idCarrello}", idCarrello)
      .replace("{psp}", psp)
      .replace("{brokerPsp}", brokerPsp)
      .replace("{channel}", channel)
      .replace("{stationPwd}", stationPassword)
      .replace("{elementiRpt}", rptsCarrello.mkString("\n"))
      .replace("{brokerPa}", brokerPa)
      .replace("{station}", station)
      .replace("{multi}", if (multibeneficiario) "<multiBeneficiario>true</multiBeneficiario>" else "")
  }

  def nodoInviaCarrelloRptParcheggioPayload(
      idCarrello: String,
      rpts: Seq[RPTKey],
      multibeneficiario: Boolean = false,
      brokerPa: String = TestItems.testIntPA,
      station: String = TestItems.stazioneOld,
      stationPassword: String = TestItems.stazionePwd
  ) = {
    val rptsCarrello = rpts.map(r => {
      val rr = payload("/requests/rpt").replace("{pa}", r.idDominio).replace("{iuv}", r.iuv).replace("{ccp}", r.ccp).replace("{amount}", "0.10")
      <elementoListaRPT>
        <identificativoDominio>{r.idDominio}</identificativoDominio>
        <identificativoUnivocoVersamento>{r.iuv}</identificativoUnivocoVersamento>
        <codiceContestoPagamento>{r.ccp}</codiceContestoPagamento>
        <tipoFirma/>
        <rpt>{XmlUtil.StringBase64Binary.encodeBase64ToString(rr.getBytes)}</rpt>
      </elementoListaRPT>
    })
    payload("/requests/nodoInviaCarrelloRPT")
      .replace("{idCarrello}", idCarrello)
      .replace("{psp}", TestItems.PSPAgid)
      .replace("{brokerPsp}", TestItems.intPSPAgid)
      .replace("{channel}", TestItems.canaleAgid)
      .replace("{stationPwd}", stationPassword)
      .replace("{elementiRpt}", rptsCarrello.mkString("\n"))
      .replace("{brokerPa}", brokerPa)
      .replace("{station}", station)
      .replace("{multi}", if (multibeneficiario) "<multiBeneficiario>true</multiBeneficiario>" else "")
  }

  def nodoInviaRPTParcheggioPayload(iuv: String, ccp: String, amount: BigDecimal = BigDecimal(10), station: String = TestItems.stazione,bollo:Boolean = false) = {
    val rpt = (if (bollo) {
      payload("/requests/rptBollo")
        .replace("{amountBollo}", "0.01")
        .replace("{amountVersamento1}", (amount - 0.01).setScale(2).toString())
        .replace("{amount}", (amount).setScale(2).toString())
    } else {
      payload("/requests/rpt")
    }).replace("{pa}", TestItems.PA).replace("{iuv}", iuv).replace("{ccp}", ccp).replace("{amount}", amount.setScale(2).toString())

    payload("/requests/nodoInviaRPT")
      .replace("{psp}", TestItems.PSPAgid)
      .replace("{brokerPsp}", TestItems.intPSPAgid)
      .replace("{channel}", TestItems.canaleAgid)
      .replace("{channelPwd}", TestItems.canalePwd)
      .replace("{pa}", TestItems.PA)
      .replace("{brokerPa}", TestItems.testIntPA)
      .replace("{station}", station)
      .replace("{stationPwd}", TestItems.stazionePwd)
      .replace("{iuv}", iuv)
      .replace("{ccp}", ccp)
      .replace("{rpt}", XmlUtil.StringBase64Binary.encodeBase64ToString(rpt.getBytes))
  }

  def nodoInviaRPTParcheggioMod3Payload(iuv: String, ccp: String, amount: BigDecimal = BigDecimal(10)) = {
    val rpt =
      payload("/requests/rpt").replace("{pa}", TestItems.PA).replace("{iuv}", iuv).replace("{ccp}", ccp).replace("{amount}", amount.setScale(2).toString())
    payload("/requests/nodoInviaRPT")
      .replace("{psp}", TestItems.PSPMod3New)
      .replace("{brokerPsp}", TestItems.intPSPMod3New)
      .replace("{channel}", TestItems.canaleMod3new)
      .replace("{channelPwd}", TestItems.canalePwd)
      .replace("{pa}", TestItems.PA)
      .replace("{brokerPa}", TestItems.testIntPA)
      .replace("{station}", TestItems.stazione)
      .replace("{stationPwd}", TestItems.stazionePwd)
      .replace("{iuv}", iuv)
      .replace("{ccp}", ccp)
      .replace("{rpt}", XmlUtil.StringBase64Binary.encodeBase64ToString(rpt.getBytes))
  }



  def nodoInviaRptParcheggio(
      iuv: String,
      ccp: String,
      testCase: Option[String] = None,
      station: Option[String] = None,
      responseAssert: NodoInviaRPTRisposta => Assertion = (_) => assert(true),
      bollo: Boolean = false
  ): Future[(NodoInviaRPTRisposta, String)] = {
    val p = Promise[Boolean]()
    val idSessione = UUID.randomUUID().toString
    val nodoInviaRPT =
      system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequestTest], p, cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
    val resinviarpt = askActor(
      nodoInviaRPT,
      SoapRequest(idSessione, nodoInviaRPTParcheggioPayload(iuv, ccp, station = station.getOrElse(TestItems.stazione),bollo = bollo), TestItems.testPDD, "nodoInviaRPT", "", Util.now(), ReExtra(), false, testCase)
    )
    assert(resinviarpt.payload.nonEmpty)
    val inviaRes: Try[NodoInviaRPTRisposta] = XmlEnum.str2nodoInviaRPTResponse_nodoperpa(resinviarpt.payload)
    assert(inviaRes.isSuccess)
    responseAssert(inviaRes.get)
    p.future.map(_ => inviaRes.get -> idSessione)
  }

  def nodoInviaRptParcheggioMod3(iuv: String, ccp: String, testCase: Option[String] = None, responseAssert: NodoInviaRPTRisposta => Assertion = (_) => assert(true)): NodoInviaRPTRisposta = {
    val nodoInviaRPT =
      system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequest], cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
    val soapres =
      askActor(nodoInviaRPT, SoapRequest(UUID.randomUUID().toString, nodoInviaRPTParcheggioMod3Payload(iuv, ccp), TestItems.testPDD, "nodoInviaRPT", "", Util.now(), ReExtra(), false, testCase))
    assert(soapres.payload.nonEmpty)
    val inviaRes: Try[NodoInviaRPTRisposta] = XmlEnum.str2nodoInviaRPTResponse_nodoperpa(soapres.payload)
    assert(inviaRes.isSuccess)
    responseAssert(inviaRes.get)
    inviaRes.get
  }

  def nodoInviaRptParcheggioMod3Future(iuv: String, ccp: String, testCase: Option[String] = None, responseAssert: NodoInviaRPTRisposta => Assertion = (_) => assert(true)): Future[NodoInviaRPTRisposta] = {
    val p = Promise[Boolean]()
    val nodoInviaRPT =
      system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequest], cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
    val soapres = askActor(
      nodoInviaRPT,
      SoapRequest(UUID.randomUUID().toString, nodoInviaRPTParcheggioMod3Payload(iuv, ccp), TestItems.testPDD, "nodoInviaRPT", "", Util.now(), ReExtra(), false, testCase)
    )
    assert(soapres.payload.nonEmpty)
    val inviaRes = XmlEnum.str2nodoInviaRPTResponse_nodoperpa(soapres.payload)
    assert(inviaRes.isSuccess)
    responseAssert(inviaRes.get)
    p.future.map(_ => {
      inviaRes.get
    })
  }

  def nodoInviaRptParcheggioMod3NoWait(iuv: String, ccp: String, testCase: Option[String] = None, actorRef: ActorRef = testActor): Unit = {
    val nodoInviaRPT =
      system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequest], cosmosRepository, props.copy(actorClassId = "nodoInviaRPT")), s"nodoInviaRPT${Util.now()}")
    nodoInviaRPT tell (SoapRequest(UUID.randomUUID().toString, nodoInviaRPTParcheggioMod3Payload(iuv, ccp), TestItems.testPDD, "nodoInviaRPT", "", Util.now(), ReExtra(), false, testCase), actorRef)
  }

  def nodoInviaRpt(
      iuv: String,
      ccp: String,
      bollo: Boolean = false,
      amount: BigDecimal = BigDecimal(10),
      versamenti :Int =  1,
      importiVersamenti:Seq[BigDecimal] = Seq(),
      canale: Option[String] = None,
      stazione: Option[String] = None,
      psp: Option[String] = None,
      pa: Option[String] = None,
      brokerPa: Option[String] = None,
      brokerPsp: Option[String] = None,
      stationPwd: Option[String] = None,
      tipoVersamento: Option[String] = None,
      iuvRpt: Option[String] = None,
      ccpRpt: Option[String] = None,
      testCase: Option[String] = None,
      ibanAddebito :Option[String] = None,
      dataEsecuzionePagamento:String = "2017-09-06",
      dataOraMessaggioRichiesta:String = "2017-09-06",
      modifyData:(ConfigData)=>ConfigData = data=>data,
      responseAssert: NodoInviaRPTRisposta => Assertion = (_) => assert(true)
  ): (NodoInviaRPTRisposta, String) = {
    val nodoInviaRPT =
      system.actorOf(Props.create(classOf[NodoInviaRPTActorPerRequest], cosmosRepository, props.copy(
        actorClassId = "nodoInviaRPT",
        ddataMap = modifyData(props.ddataMap)
      )), s"nodoInviaRPT${Util.now()}")

    val sessionid = UUID.randomUUID().toString
    val soapres = askActor(
      nodoInviaRPT,
      SoapRequest(sessionid, nodoInviaRPTPayload(
        iuv, ccp, bollo, amount = amount, canale = canale, stazione = stazione, psp = psp, pa = pa, brokerPa = brokerPa, brokerPsp = brokerPsp, stationPwd = stationPwd, tipoVersamento = tipoVersamento, iuvRpt=iuvRpt, ccpRpt=ccpRpt,versamenti= versamenti,importiVersamenti=importiVersamenti,ibanAddebito=ibanAddebito,dataEsecuzionePagamento=dataEsecuzionePagamento,dataOraMessaggioRichiesta = dataOraMessaggioRichiesta),
        TestItems.testPDD, "nodoInviaRPT", "", Util.now(), ReExtra(), idempotency = false, testCase)
    )
    assert(soapres.payload.nonEmpty)
    val inviaRes: Try[NodoInviaRPTRisposta] = XmlEnum.str2nodoInviaRPTResponse_nodoperpa(soapres.payload)
    assert(inviaRes.isSuccess)
    responseAssert(inviaRes.get)
    inviaRes.get -> sessionid
  }

  def nodoInviaCarrelloRpt(
      idCarrello: String,
      rpts: Seq[(RPTKey,String)],
      psp: String = TestItems.PSP,
      brokerPsp:String = TestItems.intPSP,
      channel: String = TestItems.canaleDifferito,
      station: String = TestItems.stazioneOld,
      testCase: Option[String] = None,
      multibeneficiario: Boolean = false,
      responseAssert: NodoInviaCarrelloRPTRisposta => Assertion = (_) => assert(true)
  ): (NodoInviaCarrelloRPTRisposta, String) = {
    val nodoInviaCarrelloRpt =
      system.actorOf(Props.create(classOf[NodoInviaCarrelloRPTActorPerRequest], cosmosRepository, props.copy(actorClassId = "nodoInviaCarrelloRpt")), s"nodoInviaCarrelloRpt${Util.now()}")

    val sessionid = UUID.randomUUID().toString
    val soapres =
      askActor(nodoInviaCarrelloRpt, SoapRequest(sessionid, nodoInviaCarrelloRptPayload(
        idCarrello,
        rpts,
        psp=psp,
        brokerPsp = brokerPsp,
        station = station,
        channel=channel,
        multibeneficiario = multibeneficiario
      ), TestItems.testPDD, "nodoInviaCarrelloRpt", "", Util.now(), ReExtra(), false, testCase))
    assert(soapres.payload.nonEmpty)
    val inviaRes: Try[NodoInviaCarrelloRPTRisposta] = XmlEnum.str2nodoInviaCarrelloRPTResponse_nodoperpa(soapres.payload)
    assert(inviaRes.isSuccess)
    responseAssert(inviaRes.get)
    inviaRes.get -> sessionid
  }





  def await[T](f: Future[T]): T = {
    Await.result(f, Duration.Inf)
  }

  def askActor(actor: ActorRef, soapRequest: SoapRequest) = {
    import akka.pattern.ask
    Await.result(actor.ask(soapRequest)(singletesttimeout).mapTo[SoapResponse], Duration.Inf)
  }
  def askActorType(actor: ActorRef, body: AnyRef) = {
    import akka.pattern.ask
    Await.result(actor.ask(body)(singletesttimeout), Duration.Inf)
  }



}
