package eu.sia.pagopa

import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.DigitPaErrorCodes
import eu.sia.pagopa.common.util.{Constant, RandomStringUtils, StringUtils, Util}
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.testutil.TestItems
import slick.jdbc.H2Profile

import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.Try

//@org.scalatest.Ignore
class RendicontazioniTests() extends BaseUnitTest {

  "invia flusso" must {
    "ok" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )
    }
    "ok ftp" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        pa = TestItems.PA_FTP,
        idFlusso = Some(idFlusso),
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )
    }
    "ko no idFlusso" in {
      inviaFlussoRendicontazione(responseAssert = (r) => {
        assert(r.esito == Constant.KO, "outcome in res")
      })
    }
    "ko flusso non valido" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        pa = TestItems.PA_FTP,
        idFlusso = Some(idFlusso),
        flussoNonValido = true,
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
          assert(r.fault.get.faultCode == DigitPaErrorCodes.PPT_SINTASSI_XSD.toString, "faultcode in res")
        }
      )
    }
    "chiedi elenco + chiedi flusso" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      log.info(s"Field idflusso=[$idFlusso]")
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )
      import H2Profile.api._
      assert(Try(runQuery[Long](fdrRepository, sql"select id from RENDICONTAZIONE where ID_FLUSSO = '#$idFlusso'".as[Long])).isSuccess)

      chiediElencoFlussiRendicontazione(responseAssert = (r) => {
        assert(r.elencoFlussiRendicontazione.isDefined)
        assert(r.elencoFlussiRendicontazione.get.idRendicontazione.nonEmpty)
        assert(r.elencoFlussiRendicontazione.get.idRendicontazione.exists(p => p.get.identificativoFlusso == idFlusso))
      })
      chiediFlussoRendicontazione(
        idFlusso,
        responseAssert = (r) => {
          assert(r.fault.isEmpty)
          assert(r.xmlRendicontazione.isDefined)
        }
      )
    }
    "ko idDominio different" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        responseAssert = (r) => {
          assert(r.esito == "OK", "outcome in res")
        }
      )
      inviaFlussoRendicontazione(
        pa = "77777777777",
        idFlusso = Some(idFlusso),
        date = Some(date),
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now().minusDays(10))
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
        }
      )
    }
    "ok dataOraFlusso busta == dataOraFlusso attachment" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now())
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment year" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusYears(1))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment month" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusMonths(1))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment day" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusDays(1))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment hour" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusHours(1))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == "KO", "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment seconds" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusSeconds(1))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
        }
      )
    }
    "ko dataOraFlusso busta != dataOraFlusso attachment millis" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val dataOraFlussoBusta = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSS").format(Util.now().minusNanos(1000000))
      val dataOraFlussoAllegato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        date = Some(date),
        dataOraFlussoBusta = Some(dataOraFlussoBusta),
        dataOraFlussoAllegato = Some(dataOraFlussoAllegato),
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )
    }
    "ko denominazioneMittente length" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      val den = "abc".padTo(150, 'a')
      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        denominazioneMittente = Option(den),
        responseAssert = (r) => {
          assert(r.esito == Constant.KO, "outcome in res")
        }
      )
    }

  }

  "chiediFlussoRendicontazione" must {
    "ok" in {
      chiediFlussoRendicontazione(
        "not exists",
        testCase = Some("ko_nexi"),
        responseAssert = (r) => {
          assert(r.fault.isDefined)
          assert(r.fault.get.faultCode == DigitPaErrorCodes.PPT_ID_FLUSSO_SCONOSCIUTO.faultCode)
          assert(r.xmlRendicontazione.isEmpty)
        }
      )
    }
  }

  "chiediElencoFlussiRendicontazione Nexi OK" in {
    val idFlussoNexiToCheck = "2023-04-01nodo-doc-dev-16818090899"
    chiediElencoFlussiRendicontazione(responseAssert = (r) => {
      assert(r.elencoFlussiRendicontazione.isDefined)
      assert(r.elencoFlussiRendicontazione.get.idRendicontazione.nonEmpty)
      val listarendi = r.elencoFlussiRendicontazione.get.idRendicontazione
      val zipped = listarendi.tail.zip(listarendi)
      assert(!zipped.exists(p => p._1 == p._2))
      assert(listarendi.exists(p => p.get.identificativoFlusso == idFlussoNexiToCheck))
    })
  }

  "chiediElencoFlussiRendicontazione Nexi KO" in {
    val idFlussoNexiToCheck = "2023-04-01nodo-doc-dev-16818090899"
    chiediElencoFlussiRendicontazione(testCase = Some("ko_nexi"), responseAssert = (r) => {
      assert(r.elencoFlussiRendicontazione.isDefined)
      assert(r.elencoFlussiRendicontazione.get.idRendicontazione.nonEmpty)
      val listarendi = r.elencoFlussiRendicontazione.get.idRendicontazione
      assert(!listarendi.exists(p => p.get.identificativoFlusso == idFlussoNexiToCheck))
    })
  }

  "chiediFlussoRendicontazione Nexi OK" in {
    val idFlussoNexiToCheck = "2023-04-01nodo-doc-dev-16818090899"
    val res = chiediFlussoRendicontazione(idFlussoNexiToCheck)
    (for {
      _ <- Future.successful(())
      _ = {
        assert(res.fault.isEmpty)
        assert(res.xmlRendicontazione.isDefined)
      }
      rendicontazioneDecoded <- Future.fromTry(StringUtils.getStringDecoded(res.xmlRendicontazione.get, true))
      flussoRiversamento = XmlEnum.str2FlussoRiversamento_flussoriversamento(rendicontazioneDecoded).getOrElse(throw exception.DigitPaException(DigitPaErrorCodes.PPT_SINTASSI_XSD))
      _ = {
        assert(flussoRiversamento.identificativoFlusso == idFlussoNexiToCheck)
      }
    } yield ())
  }

  "chiediFlussoRendicontazione KO" in {
    val idFlussoNexiToCheck = "2023-04-01nodo-doc-dev-16818090800"
    chiediFlussoRendicontazione(idFlussoNexiToCheck, Some("ko_nexi"), responseAssert = (r) => {
      assert(r.fault.isDefined)
      assert(r.fault.get.faultCode == DigitPaErrorCodes.PPT_ID_FLUSSO_SCONOSCIUTO.faultCode)
    })
  }

//  "notifyFdr OK" in {
//    val fdr = "2023-04-01nodo-doc-dev-16818090800"
//    val pspId = "nodo-doc-dev"
//    await(
//      notifyFdr(
//        fdr,
//        pspId,
//        testCase = Some("ok"),
//        responseAssert = (resp, status) => {
//          assert(status == StatusCodes.OK.intValue)
//        }
//      )
//    )
//  }

//  "getAllRevisionFdr OK" in {
//    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
//    val random = RandomStringUtils.randomNumeric(9)
//    val idFlusso = s"${date}${TestItems.PSP}-$random"
//    inviaFlussoRendicontazione(
//      idFlusso = Some(idFlusso),
//      responseAssert = (r) => {
//        assert(r.esito == Constant.OK, "outcome in res")
//      }
//    )
//    await(
//      getAllRevisionFdr(
//        "ndp", idFlusso, testCase = Some("ok"),
//        responseAssert = (resp, status) => {
//          assert(status == StatusCodes.OK.intValue)
//        }
//      )
//    )
//  }
}
