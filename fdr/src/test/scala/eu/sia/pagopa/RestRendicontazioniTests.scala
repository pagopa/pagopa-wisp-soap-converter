package eu.sia.pagopa

import akka.http.javadsl.model.StatusCodes
import eu.sia.pagopa.common.json.model.rendicontazione.GetXmlRendicontazioneResponse
import eu.sia.pagopa.common.util.xml.XmlUtil
import eu.sia.pagopa.common.util.{Constant, RandomStringUtils, Util}
import eu.sia.pagopa.commonxml.XmlEnum
import spray.json._
import eu.sia.pagopa.common.json.model._
import eu.sia.pagopa.testutil.TestItems

import java.time.format.DateTimeFormatter

//@org.scalatest.Ignore
class RestRendicontazioniTests() extends BaseUnitTest {

  "notifyFlussoRendicontazione" must {
    "ok" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      val payload = s"""{
         |  "fdr": "$idFlusso",
         |  "pspId": "${TestItems.PSP}",
         |  "retry": 1,
         |  "revision": 1
      }""".stripMargin

      await(
        notifyFlussoRendicontazione(
          Some(payload),
          testCase = Some("OK"),
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.OK.intValue)
            assert(resp.contains("{\"outcome\":\"OK\"}"))
          }
        )
      )
    }
    "ko fdr fase3 error" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      val payload =
        s"""{
           |  "fdr": "$idFlusso",
           |  "pspId": "${TestItems.PSP}",
           |  "retry": 1,
           |  "revision": 1
      }""".stripMargin

      await(
        notifyFlussoRendicontazione(
          Some(payload),
          testCase = Some("KO"),
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.INTERNAL_SERVER_ERROR.intValue)
            assert(resp.contains("{\"description\":\"Errore generico.\",\"outcome\":\"KO\"}"))
          }
        )
      )
    }
    "ko fdr fase3 error payments" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      val payload =
        s"""{
           |  "fdr": "$idFlusso",
           |  "pspId": "${TestItems.PSP}",
           |  "retry": 1,
           |  "revision": 1
      }""".stripMargin

      await({
        actorUtility.configureMocker("OK" -> { (messageType, _) => {
          messageType match {
            case "internalGetFdrPayment" => "KO"
            case _ => "OK"
          }
        }
        })

        notifyFlussoRendicontazione(
          Some(payload),
          testCase = Some("KO"),
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.INTERNAL_SERVER_ERROR.intValue)
            assert(resp.contains("{\"description\":\"Errore generico.\",\"outcome\":\"KO\"}"))
          }
        )
      })
    }
  }




}
