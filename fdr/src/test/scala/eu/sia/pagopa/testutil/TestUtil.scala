package eu.sia.pagopa.testutil

import com.typesafe.config.Config
import eu.sia.pagopa.Main.ConfigData
import org.slf4j.{Logger, LoggerFactory}

import java.net.ServerSocket
import java.time.format.DateTimeFormatter
import java.util.{Locale, TimeZone}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Success, Try}
object TestItems {

  val PA = "12345678900"
  val PA_2 = "12345678902"
  val PA_3 = "12345678903"
  val PA_old = "12345678901"
  val PA_FTP = "90000000002"
  val prefixOld = "002"
  val prefixNew = "001"
  val stazione = "stazioneTest"
  val stazioneNonIstantanea = "stazioneTestNonIstantanea"
  val stazionePV2 = "stazionePV2"
  val stazionePV2Broadcast = "stazionePV2BroadCast"
  val prefixstazionePV2 = "003"
  val stazioneOld = "stazioneTestOld"
  val paOldStazioneOld = "paOldStazioneOld"
  val stazionePwd = "canaleTestPwd"
  val codStazionePA = "02"
  val auxDigit = "0"
  val codIUV = "171920000002204"
  val PAUnknown = "01010101010"

  val PSP = "pspTest"
  val PSPAgid = "AGID_01"
  val PSPCD = "AGID_01"
  val PSPCHECKOUT = "PSP_CHECKOUT"
  val PSPECOMMERCE = "PSP_ECOMMERCE"
  val PSPMod3New = "15376371009"
  val PSPUnknown = "pspUnknown"

  val testIntPA = "intPaTest"

  val intPSP = "intPspTest"
  val intPSPAgid = "97735020584"
  val intPSPCD = "97735020584"
  val intPSPMod3New = "15376371009"
  val intPSPUnknown = "intPspUnknown"

  val canale = "canaleTest"
  val canaleImmediato = "canaleImmediato"
  val canaleDifferito = "canaleDifferito"
  val canaleAgid = "97735020584_02"
  val canaleCD = "97735020584_03"
  val canaleCHECKOUT = "97735020584_98"
  val canaleECOMMERCE = "97735020584_99"
  val canaleMod3new = "15376371009_01"
  val canalePayPal = "canalePayPal"
  val canaleBPay = "canaleBPay"
  val canaleNodoPay = "canaleNodoPay"
  val canaleNodoPayV1 = "canaleNodoPayV1"
  val canaleBPayWrongModello = "canaleBPayWrongModello"
  val canaleNodoPayWrongModello = "canaleNodoPayWrongModello"
  val canalePull = "canalePull"
  val canaleIrr = "canaleIrraggiungibile"
  val canaleUnknown = "canaleUnknown"

  val canalePwd = "canaleTestPwd"

  val testDTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
  val httpConnectTimeout: FiniteDuration = 10.seconds
  val testPDD = "portadidominio"
  val pptSignCertPath = "/opt/configuration/CASogeiTest.pem"

  val ddataMap: ConfigData = TestDData.ddataMap
}
object TestUtil {

  Locale.setDefault(Locale.ITALIAN)
  TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))

  val log: Logger = LoggerFactory.getLogger(getClass.getName)
  val MANPORTDIFF = 100
  val USEDISCOVERY = true

  val startPort = 2551
  val endPort = 2570
  var currentPort = 0
  var usedPorts: Seq[Int] = Nil

  val host = "localhost"

  def makeConfigWrapper(actorSystemName: String, config: Config): Config = {
    sys.env.getOrElse("REMOTING_BIND_HOST", throw new IllegalArgumentException("Bundle hostname name must be defined by the REMOTING_BIND_HOST property"))
    sys.env.getOrElse("REMOTING_BIND_PORT", throw new IllegalArgumentException("Bundle port name must be defined by the REMOTING_BIND_PORT property"))

    config

  }

  def findUsedPort(startPort: Int, endPort: Int): Int = {
    import java.net.{Socket, SocketException}

    (startPort to endPort)
      .flatMap(port => {
        if (port != TestUtil.currentPort + MANPORTDIFF) {
          try {
            new Socket(host, port).close()
            Some(port)
          } catch {
            case _: SocketException =>
              None
          }
        } else {
          None
        }

      })
      .headOption
      .getOrElse(TestUtil.currentPort + MANPORTDIFF)

  }

  def getAllUsedPorts(startPortz: Int = startPort, currentPort: Int): Seq[Int] = {
    val x = (0 to 20).map(i => {
      val port = startPortz + i
      (for {
        s <- Try(new ServerSocket(port))
        _ <- Try(s.close())
        _ = log.info(s"port $port not is used")
      } yield None).recoverWith({ case e =>
        log.info(s"port $port is used")
        Success(Some(port))
      })
    })
    x.filter(d => d.isSuccess && d.get.isDefined).map(_.get.get) ++ Seq(currentPort)
  }

  def checkPort(startPortz: Int = startPort): Int = {
    log.debug(s"check port $startPortz")
    import java.net.Socket
    var s: Socket = null
    var port = startPortz
    try {
      s = new Socket(host, port)
      port = startPortz + 1
    } catch {
      case _: Exception =>
        log.info(s"Trovata Porta libera $port")
        if (port > endPort) {
          throw new Exception(s"Out of range [$startPort-$endPort]")
        }
    } finally {
      if (s != null) {
        try {
          s.close()
        } catch {
          case _: Exception =>
            log.info("non si è chiusa la socket :-(, probabilmente dovrò farlo a mano")
        }
      }
    }
    if (startPortz != port) {
      port = checkPort(startPortz + 1)
    }
    port
  }

}
