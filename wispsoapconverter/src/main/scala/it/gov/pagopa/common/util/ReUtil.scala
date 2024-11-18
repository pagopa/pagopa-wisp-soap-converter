package it.gov.pagopa.common.util

import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.enums.WorkflowStatus
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.common.util.azure.cosmos.EventCategory
import org.slf4j.MDC

trait ReUtil {
  this: NodoLogging =>

  def traceWebserviceInvocation(request: SoapRequest, response: SoapResponse, re: Re, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, Some(request.payload))
    Util.logPayload(log, Some(response.payload))
    val webserviceInvocationEvent = ReRequest(
      sessionId = request.sessionId,
      testCaseId = request.testCaseId,
      re = re.copy(
        sessionId = Some(MDC.get(Constant.MDCKey.SESSION_ID)),
        status = Some(WorkflowStatus.TRIGGER_PRIMITIVE_PROCESSED.toString),
        insertedTimestamp = request.timestamp,
        requestPayload = Some(request.payload.getUtf8Bytes),
        responsePayload = Some(response.payload.getUtf8Bytes),
        eventCategory = EventCategory.INTERFACE,
        outcome = Some(MDC.get(Constant.MDCKey.PROCESS_OUTCOME)),
        errorLine = Some(MDC.get(Constant.MDCKey.ERROR_LINE)),
        businessProcess = Some(request.primitive)
      ),
      reExtra = Some(reExtra)
    )
    reEventFunc(webserviceInvocationEvent, log, ddataMap)
  }

}
