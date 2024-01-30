package eu.sia.pagopa

import eu.sia.pagopa.common.message.{BlobBodyRef, ReEventHub}
import eu.sia.pagopa.common.util.azurehubevent.AppObjectMapper
import eu.sia.pagopa.common.util.{RandomStringUtils, Util}
import net.openhft.hashing.LongHashFunction
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class StaticUnitTests() extends AnyFlatSpec with should.Matchers {

  "fdr re eventhub json" should "ok" in {
    val now = Util.now()

    val testDTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    val created = LocalDateTime.parse(testDTF.format(now))
    val sessionIdOriginal = UUID.randomUUID().toString
    val sessionId = UUID.randomUUID().toString
    val dataOraEvento = now.toString
    val status = "OK"
    val sottoTipoEvento = "REQ"
    val erogatore = "NDP_FDR"
    val fruitore = "nodo-doc-dev"
    val stazione = "nodo-doc-dev"
    val noticeNumber = s"${TestItems.prefixNew}${RandomStringUtils.randomNumeric(15)}"
    val paymentToken = UUID.randomUUID().toString
    val idDominio = "00000000099"
    val iuv = RandomStringUtils.randomNumeric(15)
    val ccp = "n/a"
    val fileName = s"${sessionId}_nodoInviaFlussoRendicontazione_RES"
    val psp = "nodo-doc-dev"
    val flowId = RandomStringUtils.randomNumeric(11)
    val flowName = s"$dataOraEvento$psp-$flowId"
    val flowAction = "nodoInviaFlussoRendicontazione"
    val payload: Option[String] = None
    val uniqueId: String = s"${dataOraEvento.substring(0, 10)}_${
      LongHashFunction
        .xx()
        .hashChars(s"$dataOraEvento$sessionId$sessionIdOriginal$status$sottoTipoEvento$erogatore$fruitore$stazione$noticeNumber$paymentToken$idDominio$iuv$ccp$info")
    }"

    val reEventHub = ReEventHub(
      "FDR001",
      uniqueId,
      created,
      Some(sessionId),
      "INTERFACE",
      None,
      Some(flowName),
      Some(psp),
      Some(idDominio),
      Some(flowAction),
      sottoTipoEvento,
      Some("POST"),
      None,
      Some(BlobBodyRef(Some("pagopadweufdrresa"), Some("payload"), Some(fileName), (payload.map(_.length).getOrElse(0)))),
      Map()
    )

    val x = AppObjectMapper.objectMapper.writeValueAsString(reEventHub)
    val y =
      s"""
         |{
         |  "serviceIdentifier": "FDR001",
         |  "uniqueId": "$uniqueId",
         |  "created": "${created.toString}",
         |  "sessionId": "$sessionId",
         |  "eventType": "INTERFACE",
         |  "fdr": "$flowName",
         |  "pspId": "nodo-doc-dev",
         |  "organizationId": "00000000099",
         |  "fdrAction": "nodoInviaFlussoRendicontazione",
         |  "httpType": "REQ",
         |  "httpMethod": "POST",
         |  "httpUrl": null,
         |  "blobBodyRef": {
         |    "storageAccount": "pagopadweufdrresa",
         |    "containerName": "payload",
         |    "fileName": "$fileName",
         |    "fileLength": ${payload.map(_.length).getOrElse(0)}
         |  },
         |  "header": {}
         |}""".stripMargin.replace(" ", "").replace("\n", "").replace("\r", "")
    assert(x == y)
  }

}
