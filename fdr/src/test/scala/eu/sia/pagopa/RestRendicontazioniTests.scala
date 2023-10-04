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

  "getAllFdr" must {
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

      await(
        getAllRevisionFdr(
          TestItems.PA,
          idFlusso,
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.OK.intValue)
            val respObject = resp.parseJson.convertTo[GetXmlRendicontazioneResponse]
            assert(respObject.xmlRendicontazione.nonEmpty)
            val ctFlussoRiversamento = XmlEnum.str2FlussoRiversamento_flussoriversamento(XmlUtil.StringBase64Binary.decodeBase64(respObject.xmlRendicontazione))
            assert(ctFlussoRiversamento.get.identificativoFlusso == idFlusso)
          }
        )
      )
    }
    "ko pa not found" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"
      val paNotFound = "paNotFound"
      await(
        getAllRevisionFdr(
          paNotFound,
          idFlusso,
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.BAD_REQUEST.intValue())
            val respObject = resp.parseJson.convertTo[Error]
            assert(respObject.error == s"idPA $paNotFound not found")
          }
        )
      )
    }
    "ko pa not enabled" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      await(
        getAllRevisionFdr(
          TestItems.PA_DISABLED,
          idFlusso,
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.BAD_REQUEST.intValue())
            val respObject = resp.parseJson.convertTo[Error]
            assert(respObject.error == s"idPA ${TestItems.PA_DISABLED} found but disabled")
          }
        )
      )
    }
    "ko flusso not found" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      await(
        getAllRevisionFdr(
          TestItems.PA,
          idFlusso,
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.NOT_FOUND.intValue())
            val respObject = resp.parseJson.convertTo[Error]
            assert(respObject.error == "FdR unknown or not available")
          }
        )
      )
    }
    "ko flusso not in db" in {
      val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Util.now())
      val random = RandomStringUtils.randomNumeric(9)
      val idFlusso = s"${date}${TestItems.PSP}-$random"

      inviaFlussoRendicontazione(
        idFlusso = Some(idFlusso),
        pa = TestItems.PA_FTP,
        responseAssert = (r) => {
          assert(r.esito == Constant.OK, "outcome in res")
        }
      )

      await(
        getAllRevisionFdr(
          TestItems.PA_FTP,
          idFlusso,
          responseAssert = (resp, status) => {
            assert(status == StatusCodes.NOT_FOUND.intValue())
            val respObject = resp.parseJson.convertTo[Error]
            assert(respObject.error == "FdR XML not found")
          }
        )
      )
    }
  }

}
