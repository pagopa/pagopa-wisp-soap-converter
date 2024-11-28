package it.gov.pagopa.tests

import it.gov.pagopa.common.message.{Re, ReExtra}
import it.gov.pagopa.common.util.AppLogger
import it.gov.pagopa.common.util.azure.Appfunction
import it.gov.pagopa.common.util.azure.cosmos.{Esito, EventCategory}
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
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault> <faultString>fString</faultString> <description>fDescr</description>
          </xml>.toString().replaceAll("[\\s\\n\\t]+", "").getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request =>
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |payload: [<xml><fault><faultCode>fCode</faultCode></fault><faultString>fString</faultString><description>fDescr</description></xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>
            <fault>
              <faultCode>fCode</faultCode>
            </fault> <faultString>fString</faultString> <description>fDescr</description>
          </xml>.toString().replaceAll("[\\s\\n\\t]+", "").getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request =>
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |payload: [<xml><fault><faultCode>fCode</faultCode></fault><faultString>fString</faultString><description>fDescr</description></xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request =>
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |payload: [<xml>test</xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get ==
      """Re Request =>
        |httpUri: [UNKNOWN]
        |httpHeaders: [UNKNOWN]
        |httpStatusCode: [UNKNOWN]
        |payload: [<xml>test</xml>]""".stripMargin)

    assert(Appfunction.fmtMessage(
      Re(
        Instant.now(), EventCategory.INTERNAL,
        requestPayload = Some(
          <xml>test</xml>.toString().getBytes
        )
      ),
      Some(ReExtra())
    ).get == """Re Request => ESITO[None] STATO[STATO non presente]""")

  }

  "fmtMessageJson" should "ok" in {
    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
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
    ).get == """{"internalMessage":"Tipo di REQ/RESP non identificata per sotto tipo evento non valido","categoriaEvento":"INTERFACE","caller":"nd","httpType":"nd","esito":"KO","faultCode":"fCode","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
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
    ).get == """{"internalMessage":"Tipo di REQ/RESP non identificata per sotto tipo evento non valido","categoriaEvento":"INTERFACE","caller":"nd","httpType":"nd","esito":"KO","faultCode":"fCode","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"Tipo di REQ/RESP non identificata per sotto tipo evento non valido","categoriaEvento":"INTERFACE","caller":"nd","httpType":"nd","esito":"OK","subject":"nd","subjectDescr":"nd"}""")

    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), EventCategory.INTERFACE,
        outcome = Some(Esito.OK.toString),
        requestPayload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"Tipo di REQ/RESP non identificata per sotto tipo evento non valido","categoriaEvento":"INTERFACE","caller":"nd","httpType":"nd","esito":"OK","subject":"nd","subjectDescr":"nd"}""")


    assert(Appfunction.fmtMessageJson(
      Re(
        Instant.now(), EventCategory.INTERNAL,
        requestPayload = Some(
          <xml>test
          </xml>.toString().getBytes
        )
      ),
      Some(ReExtra()), TestItems.ddataMap
    ).get == """{"internalMessage":"Cambio stato in [nd]","categoriaEvento":"INTERNAL","esito":"OK"}""")

  }


}
