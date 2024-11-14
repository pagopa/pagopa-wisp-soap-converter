package it.gov.pagopa.common.util

import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.common.util.azure.cosmos.{Esito, EventCategory}
import org.slf4j.MDC

trait ReUtil {
  this: NodoLogging =>

  def traceInterfaceRequest(message: SoapRequest, re: Re, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, Some(message.payload))
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = re.copy(
        sessionId = Some(MDC.get(Constant.MDCKey.SESSION_ID)),
        insertedTimestamp = message.timestamp,
        requestPayload = Some(message.payload.getUtf8Bytes),
        responsePayload = None,
        eventCategory = EventCategory.INTERFACE,
        outcome = Some(Esito.OK),
        businessProcess = Some(message.primitive)
      ),
      reExtra = Some(reExtra)
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

}
