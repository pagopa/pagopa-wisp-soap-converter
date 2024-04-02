package it.gov.pagopa.common.util

import it.gov.pagopa.common.actor.NodoLogging
import it.gov.pagopa.common.enums.EsitoRE
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc

trait ReUtil { this: NodoLogging =>

  def traceInterfaceRequest(message: SoapRequest, re: Re, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, Some(message.payload))
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = re.copy(
        insertedTimestamp = message.timestamp,
        payload = Some(message.payload.getUtf8Bytes),
        categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
        sottoTipoEvento = SottoTipoEvento.REQ.toString,
        esito = Some(EsitoRE.RICEVUTA.toString),
        businessProcess = Some(message.primitive)
      ),
      reExtra = Some(reExtra)
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

}
