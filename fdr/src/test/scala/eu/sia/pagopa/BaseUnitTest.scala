package eu.sia.pagopa

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.offline.OfflineRepository
import eu.sia.pagopa.common.repo.{DBComponent, Repositories}
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.sdkazureclient.AzureProducerBuilder
import eu.sia.pagopa.common.util.xml.XmlUtil
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.{NodoChiediElencoFlussiRendicontazioneActorPerRequest, NodoChiediFlussoRendicontazioneActorPerRequest, NodoInviaFlussoRendicontazioneActorPerRequest}
import eu.sia.pagopa.testutil._
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import liquibase.{Contexts, Liquibase}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, BeforeAndAfterAll}
import scalaxbmodel.nodoperpa.{NodoChiediElencoFlussiRendicontazioneRisposta, NodoChiediFlussoRendicontazioneRisposta}
import scalaxbmodel.nodoperpsp.NodoInviaFlussoRendicontazioneRisposta
import slick.dbio.{DBIO, DBIOAction, Streaming}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.{H2Profile, JdbcBackend}
import slick.sql.SqlStreamingAction
import slick.util.AsyncExecutor

import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object RepositoriesUtil {
  var offlineRepository: OfflineRepository = _

  def getOfflineRepository(implicit ec: ExecutionContext): OfflineRepository = {
    if (offlineRepository != null) offlineRepository
    else {
      offlineRepository = OfflineRepository(H2Profile, DBUtils.initDB("NODO_OFFLINE", "offline", Seq("dev")))
      offlineRepository
    }
  }
}

//@org.scalatest.Ignore
abstract class BaseUnitTest()
    extends TestKit({
      val config =
        """
        forwarder {
            subscriptionKey=key
        }
        azure-hub-event.azure-sdk-client {
          re-event {
            client-timeoput-ms = 5000
            event-hub-name = "nodo-dei-pagamenti-re"
            connection-string = "Endpoint=sb://pagopa-d-evh-ns01.servicebus.windows.net/;SharedAccessKeyName=nodo-dei-pagamenti-SIA;SharedAccessKey=2yd25SPaGDpniGuB4jdBTsTLT7K8P5po6pm0lGfG3YI=;EntityPath=nodo-dei-pagamenti-re"
          }
        }
        azure-hub-event.azure-sdk-client{
          biz-event {
            client-timeoput-ms = 5000
            event-hub-name = "nodo-dei-pagamenti-biz-evt"
            connection-string = "Endpoint=sb://pagopa-d-evh-ns01.servicebus.windows.net/;SharedAccessKeyName=pagopa-biz-evt-tx;SharedAccessKey=rU6qCxfy91XJb0U6gN+17wY8vgb8o2Ojb/vNZHs0tgo=;EntityPath=nodo-dei-pagamenti-biz-evt"
          }
        }
        eventhub-dispatcher {
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
        limitjobs = true
        config.ftp.connect-timeout = "1000"
        rendicontazionibollo.path = "/tmp"
        slick.dbs.default.db.numThreads=20
        slick.dbs.default.db.maxConnections=20
        configScheduleMinutes=1
        coordinatedShutdown=true
        waitAsyncProcesses=true
        reBufferSize=1
        reFlushIntervalSeconds=1
        sendPaymentResult{
          v1 {
            uri="https://acardste.vaservices.eu:1443/pp-restapi-CD/v2/payments/send-payment-result"
            proxyUse="true"
            proxyHost="10.97.20.33"
            proxyPort=80
            #proxyUser=""
            #proxyPassword=""
            timeoutSeconds=80
          }
          v2 {
            uri="https://acardste.vaservices.eu:1443/pp-restapi-CD/v2/transactions/{idTransaction}/user-receipts"
             subscriptionKey=33e3cb3333333c3ea33be3a333efb3ba
            proxyUse="true"
            proxyHost="10.97.20.33"
            proxyPort=80
            #proxyUser=""
            #proxyPassword=""
            timeoutSeconds=80
          }
        }
        gec {
          fees {
            uri="http://localhost:8088/fees"
            subscriptionKey=6e508a628317485ea1241e57cde7602d
            proxyUse="false"
            proxyHost="10.79.20.33"
            proxyPort=80
            #proxyUser=""
            #proxyPassword=""
            timeoutSeconds=80
          }
        }
    """
      ActorSystem("testSystem", ConfigFactory.parseString(config))
    })
    with AnyWordSpecLike
    with ImplicitSender
    with should.Matchers
    with BeforeAndAfterAll {

  override def afterAll() {

    Thread.sleep(2000)
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll() {
    import slick.jdbc.H2Profile.api._
    offlineRepository.db.run(sql"update SCHEDULER_FIRE_CHECK set STATUS = 'WAIT_TO_NEXT_FIRE'".as[Long])
  }

  system.registerOnTermination(() => {
//    Thread.sleep(5000)
//    offlineRepository.db.close()
  })

  implicit val log: NodoLogger = new NodoLogger(Logging(system, getClass.getCanonicalName))
  implicit val ec: ExecutionContext = system.dispatcher

  val offlineRepository: OfflineRepository = RepositoriesUtil.getOfflineRepository

  val actorUtility = new ActorUtilityTest()
  val reFunction = AzureProducerBuilder.build()

  val certPath = s"${new File(".").getCanonicalPath}/devops/localresources/cacerts"
  val caSogeiPath = s"${new File(".").getCanonicalPath}/devops/localresources/CASogeiTest.pem"

  class RepositoriesTest(override val config: Config, override val log: NodoLogger) extends Repositories(config, log) {
    override lazy val offlineRepository: OfflineRepository = RepositoriesUtil.getOfflineRepository
  }
  val repositories = new RepositoriesTest(system.settings.config, log)

  val props = ActorProps(null, null, null, actorUtility, Map(), reFunction, "", caSogeiPath,certPath, TestItems.ddataMap)

  val mockActor = system.actorOf(Props.create(classOf[MockActor]), s"mock")

  val singletesttimeout: FiniteDuration = 1000.seconds

  def payload(testfile: String): String = {
    SpecsUtils.loadTestXML(s"$testfile.xml")
  }

  def initDB(schema: String, folder: String, additionalContexts: Seq[String] = Seq()): JdbcBackend.DatabaseDef = {

    val path = System.getProperty("user.dir")
    val scriptpath = s"$path/devops/db/liquibase/changelog/$folder/"
    val scriptFolder = new File(scriptpath)
    val changelogMaster = s"./db.changelog-master.xml"

    val db = Database.forURL(
      s"jdbc:h2:$path/target/$schema;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS $schema\\;SET SCHEMA $schema;",
      driver = "org.h2.Driver",
      executor = AsyncExecutor("test1", minThreads = 10, queueSize = 1000, maxConnections = 10, maxThreads = 10)
    )
    val database =
      DatabaseFactory.getInstance.findCorrectDatabaseImplementation(new JdbcConnection(db.source.createConnection()))
    val resourceAccessorOnline = new FileSystemResourceAccessor(scriptFolder)
    val liqui = new Liquibase(changelogMaster, resourceAccessorOnline, database)
    liqui.update(new Contexts(("default" + additionalContexts.mkString(",", ",", ""))))
    liqui.close()
    db
  }

  def inviaFlussoRendicontazionePayload(
      psp: String = TestItems.PSP,
      brokerPsp: String = TestItems.intPSP,
      channel: String = TestItems.canale,
      channelPwd: String = TestItems.canalePwd,
      pa: String = TestItems.PA,
      idFlussoReq: Option[String] = None,
      dateReq: Option[String],
      dataOraFlussoBusta: Option[String],
      dataOraFlussoAllegato: Option[String],
      istitutoMittente: Option[String],
      flussoNonValido: Boolean = false,
      denominazioneMittente : String
  ) = {
    val date = dateReq.getOrElse(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now()))
    val _dataOraFlussoBusta = dataOraFlussoBusta.getOrElse(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now()))
    val _dataOraFlussoAllegato = dataOraFlussoAllegato.getOrElse(_dataOraFlussoBusta)
//    val idflusso = idFlussoReq.getOrElse(s"${date}${psp}-${RandomStringUtils.randomNumeric(9)}")
    val rendi = if (flussoNonValido) {
      "flusso non valido"
    } else {
      SpecsUtils
        .loadTestXML("/requests/rendicontazione.xml")
        .replace("{istitutoMittente}", istitutoMittente.getOrElse(TestItems.PSP))
        .replace("{idflusso}", idFlussoReq.getOrElse(""))
        .replace("{dataoraflussoallegato}", _dataOraFlussoAllegato)
        .replace("{date}", date)
        .replace("{denominazioneMittente}", denominazioneMittente)
    }
    val payloadNew = payload("/requests/nodoInviaFlussoRendicontazione")
      .replace("{psp}", psp)
      .replace("{brokerPsp}", brokerPsp)
      .replace("{channel}", channel)
      .replace("{channelPwd}", channelPwd)
      .replace("{pa}", pa)
      .replace("{idflusso}", idFlussoReq.getOrElse(""))
      .replace("{dataoraflussobusta}", _dataOraFlussoBusta)
      .replace("{rendicontazione}", XmlUtil.StringBase64Binary.encodeBase64ToString(rendi.getBytes))
    payloadNew
  }

  def chiediFlussoRendicontazionePayload(
      idFlusso: String,
      psp: String = TestItems.PSP,
      brokerPsp: String = TestItems.intPSP,
      channel: String = TestItems.canale,
      channelPwd: String = TestItems.canalePwd,
      pa: String = TestItems.PA,
      brokerPa: String = TestItems.testIntPA,
      station: String = TestItems.stazione,
      stationPwd: String = TestItems.stazionePwd
  ) = {

    payload("/requests/nodoChiediFlussoRendicontazione")
      .replace("{psp}", psp)
      .replace("{brokerPsp}", brokerPsp)
      .replace("{channel}", channel)
      .replace("{channelPwd}", channelPwd)
      .replace("{pa}", pa)
      .replace("{brokerPa}", brokerPa)
      .replace("{station}", station)
      .replace("{stationPwd}", stationPwd)
      .replace("{idflusso}", idFlusso)
  }

  def chiediElencoFlussiRendicontazionePayload(
      psp: String = TestItems.PSP,
      brokerPsp: String = TestItems.intPSP,
      channel: String = TestItems.canale,
      channelPwd: String = TestItems.canalePwd,
      pa: String = TestItems.PA,
      brokerPa: String = TestItems.testIntPA,
      station: String = TestItems.stazione,
      stationPwd: String = TestItems.stazionePwd
  ) = {

    payload("/requests/nodoChiediElencoFlussiRendicontazione")
      .replace("{psp}", psp)
      .replace("{brokerPsp}", brokerPsp)
      .replace("{channel}", channel)
      .replace("{channelPwd}", channelPwd)
      .replace("{pa}", pa)
      .replace("{brokerPa}", brokerPa)
      .replace("{station}", station)
      .replace("{stationPwd}", stationPwd)
  }


  def inviaFlussoRendicontazione(
      testCase: Option[String] = None,
      pa: String = TestItems.PA,
      idFlusso: Option[String] = None,
      date: Option[String] = None,
      dataOraFlussoBusta: Option[String] = None,
      dataOraFlussoAllegato: Option[String] = None,
      istitutoMittente: Option[String] = None,
      flussoNonValido: Boolean = false,
      responseAssert: NodoInviaFlussoRendicontazioneRisposta => Assertion = (_) => assert(true),
      denominazioneMittente: Option[String] = Option("Banca Sella")
  ): NodoInviaFlussoRendicontazioneRisposta = {
    val act =
      system.actorOf(
        Props.create(classOf[NodoInviaFlussoRendicontazioneActorPerRequest], repositories, props.copy(actorClassId = "inviaflussorendi", routers = Map("ftp-senderRouter" -> mockActor))),
        s"inviaflussorendi${Util.now()}"
      )
    val soapres = askActor(
      act,
      SoapRequest(
        UUID.randomUUID().toString,
        inviaFlussoRendicontazionePayload(
          pa = pa,
          idFlussoReq = idFlusso,
          dateReq = date,
          dataOraFlussoBusta = dataOraFlussoBusta,
          dataOraFlussoAllegato = dataOraFlussoAllegato,
          istitutoMittente = istitutoMittente,
          flussoNonValido = flussoNonValido,
          denominazioneMittente = denominazioneMittente.get
        ),
        TestItems.testPDD,
        "inviaflussorendi",
        "test",
        Util.now(),
        ReExtra(),
        testCase
      )
    )
    assert(soapres.payload.isDefined)
    val actRes: Try[NodoInviaFlussoRendicontazioneRisposta] = XmlEnum.str2nodoInviaFlussoRendicontazioneResponse_nodoperpsp(soapres.payload.get)
    assert(actRes.isSuccess)
    responseAssert(actRes.get)
    actRes.get
  }

  def chiediFlussoRendicontazione(
      idFlusso: String,
      testCase: Option[String] = None,
      responseAssert: NodoChiediFlussoRendicontazioneRisposta => Assertion = (_) => assert(true)
  ): NodoChiediFlussoRendicontazioneRisposta = {
    val act =
      system.actorOf(Props.create(classOf[NodoChiediFlussoRendicontazioneActorPerRequest], repositories, props.copy(actorClassId = "chiediflussorendi")), s"chiediflussorendi${Util.now()}")
    val soapres =
      askActor(act, SoapRequest(UUID.randomUUID().toString, chiediFlussoRendicontazionePayload(idFlusso), TestItems.testPDD, "chiediflussorendi", "test", Util.now(), ReExtra(), testCase))
    assert(soapres.payload.isDefined)
    val actRes: Try[NodoChiediFlussoRendicontazioneRisposta] = XmlEnum.str2nodoChiediFlussoRendicontazioneResponse_nodoperpa(soapres.payload.get)
    assert(actRes.isSuccess)
    responseAssert(actRes.get)
    actRes.get
  }

  def chiediElencoFlussiRendicontazione(
      testCase: Option[String] = None,
      responseAssert: NodoChiediElencoFlussiRendicontazioneRisposta => Assertion = (_) => assert(true)
  ): NodoChiediElencoFlussiRendicontazioneRisposta = {
    val act =
      system.actorOf(
        Props.create(classOf[NodoChiediElencoFlussiRendicontazioneActorPerRequest], repositories, props.copy(actorClassId = "chiedielencoflussorendi")),
        s"chiedielencoflussorendi${Util.now()}"
      )
    val soapres =
      askActor(act, SoapRequest(UUID.randomUUID().toString, chiediElencoFlussiRendicontazionePayload(), TestItems.testPDD, "chiedielencoflussorendi", "test", Util.now(), ReExtra(), testCase))
    assert(soapres.payload.isDefined)
    val actRes: Try[NodoChiediElencoFlussiRendicontazioneRisposta] = XmlEnum.str2nodoChiediElencoFlussiRendicontazioneResponse_nodoperpa(soapres.payload.get)
    assert(actRes.isSuccess)
    responseAssert(actRes.get)
    actRes.get
  }

  def await[T](f: Future[T]): T = {
    Await.result(f, Duration.Inf)
  }
  def askActor(actor: ActorRef, restRequest: RestRequest) = {
    import akka.pattern.ask
    Await.result(actor.ask(restRequest)(singletesttimeout).mapTo[RestResponse], Duration.Inf)
  }

  def askActor(actor: ActorRef, soapRequest: SoapRequest) = {
    import akka.pattern.ask
    Await.result(actor.ask(soapRequest)(singletesttimeout).mapTo[SoapResponse], Duration.Inf)
  }

  def askActor(actor: ActorRef, wr: WorkRequest) = {
    import akka.pattern.ask
    Await.result(actor.ask(wr)(singletesttimeout).mapTo[WorkResponse], Duration.Inf)
  }

  def runQuery[T](repo: DBComponent, action: DBIOAction[Vector[T], Streaming[T], slick.dbio.Effect]) = {
    Await.result(repo.db.run(action), Duration.Inf).head
  }
  def runQueryList[T](repo: DBComponent, action: SqlStreamingAction[Vector[T], T, slick.dbio.Effect]) = {
    Await.result(repo.db.run(action), Duration.Inf)
  }

  def runAction[M](repo: DBComponent, action: DBIO[M]): M = {
    Await.result(repo.runAction(action), Duration.Inf)
  }

}
