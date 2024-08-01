package it.gov.pagopa.tests

import it.gov.pagopa.common.message.{Re, ReExtra}
import it.gov.pagopa.common.util.AppLogger
import it.gov.pagopa.common.util.azure.Appfunction
import it.gov.pagopa.common.util.azure.cosmos.{CategoriaEvento, Componente, Esito, SottoTipoEvento}
import it.gov.pagopa.tests.testutil.TestItems
import org.mockito.MockitoSugar.mock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.time.Instant

class FormatUnitTests extends AnyFlatSpec with should.Matchers {

  val log = mock[AppLogger]

  "formatHeaders" should "ok" in {
    val x = Appfunction.formatHeaders(
      Some(Seq(
        "h1" -> "h_1"
      ))
    )
    assert(x == "h1=h_1")
  }

  "fmtMessage" should "ok" in {

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.REQ,
        esito = Esito.RECEIVED,
        payload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault> <faultString>fString</faultString> <description>fDescr</description>
          </xml>.toString().replaceAll("[\\s\\n\\t]+", "").getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request => TIPO_EVENTO[REQ/n.a] FRUITORE[n.a] EROGATORE[n.a] ESITO[RECEIVED] DETTAGLIO[Il nodo ha ricevuto un messaggio]
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |elapsed: [UNKNOWN]
        |payload: [<xml><fault><faultCode>fCode</faultCode></fault><faultString>fString</faultString><description>fDescr</description></xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.RESP,
        esito = Esito.SEND,
        payload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault> <faultString>fString</faultString> <description>fDescr</description>
          </xml>.toString().replaceAll("[\\s\\n\\t]+", "").getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request => TIPO_EVENTO[RESP/n.a] FRUITORE[n.a] EROGATORE[n.a] ESITO[SEND] DETTAGLIO[Il nodo ha risposto al messaggio ricevuto]
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |elapsed: [UNKNOWN]
        |payload: [<xml><fault><faultCode>fCode</faultCode></fault><faultString>fString</faultString><description>fDescr</description></xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.RESP,
        esito = Esito.SEND,
        payload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request => TIPO_EVENTO[RESP/n.a] FRUITORE[n.a] EROGATORE[n.a] ESITO[SEND] DETTAGLIO[Il nodo ha risposto al messaggio ricevuto]
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |elapsed: [UNKNOWN]
        |payload: [<xml>test</xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.REQ,
        esito = Esito.RECEIVED,
        payload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request => TIPO_EVENTO[REQ/n.a] FRUITORE[n.a] EROGATORE[n.a] ESITO[RECEIVED] DETTAGLIO[Il nodo ha ricevuto un messaggio]
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |elapsed: [UNKNOWN]
        |payload: [<xml>test</xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERNAL,
        SottoTipoEvento.INTERN,
        esito = Esito.EXECUTED_INTERNAL_STEP,
        payload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get == """Re Request => TIPO_EVENTO[INTERN/n.a] ESITO[EXECUTED_INTERNAL_STEP] STATO[STATO non presente]""")

  }

  "fmtMessageJson" should "ok" in {
    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.REQ,
        esito = Esito.RECEIVED,
        payload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault>
            <faultString>fString</faultString>
            <description>fDescr</description>
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"SERVER --> REQUEST: messaggio da [subject:nd]","categoriaEvento":"INTERFACE","caller":"SERVER","httpType":"REQUEST","esito":"KO","faultCode":"fCode","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.RESP,
        esito = Esito.SEND,
        payload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault>
            <faultString>fString</faultString>
            <description>fDescr</description>
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"SERVER --> RESPONSE: risposta a [subject:nd] [esito:KO] [faultCode:fCode]","categoriaEvento":"INTERFACE","caller":"SERVER","httpType":"RESPONSE","esito":"KO","faultCode":"fCode","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.RESP,
        esito = Esito.SEND,
        payload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"SERVER --> RESPONSE: risposta a [subject:nd] [esito:OK]","categoriaEvento":"INTERFACE","caller":"SERVER","httpType":"RESPONSE","esito":"OK","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERFACE,
        SottoTipoEvento.REQ,
        esito = Esito.RECEIVED,
        payload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"SERVER --> REQUEST: messaggio da [subject:nd]","categoriaEvento":"INTERFACE","caller":"SERVER","httpType":"REQUEST","esito":"OK","subject":"nd","subjectDescr":"nd"}""")


    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), Componente.WISP_SOAP_CONVERTER, CategoriaEvento.INTERNAL,
        SottoTipoEvento.INTERN,
        esito = Esito.EXECUTED_INTERNAL_STEP,
        payload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"Cambio stato in [nd]","categoriaEvento":"INTERNAL","esito":"OK"}""")

  }


}
