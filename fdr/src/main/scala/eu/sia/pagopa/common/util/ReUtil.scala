package eu.sia.pagopa.common.util

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.NodoLogging
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc

trait ReUtil { this: NodoLogging =>

  def traceInterfaceRequest(message: SoapRequest, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, Some(message.payload))
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = Re(
        componente = Componente.NDP_FDR.toString,
        categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
        sottoTipoEvento = SottoTipoEvento.REQ.toString,
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(message.sessionId),
        payload = Some(message.payload.getUtf8Bytes),
        insertedTimestamp = message.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(message.primitive)
      ),
      reExtra = Some(reExtra)
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

  def traceInterfaceRequest(message: RestRequest, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, message.payload)
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = Re(
        componente = Componente.NDP_FDR.toString,
        categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
        sottoTipoEvento = SottoTipoEvento.REQ.toString,
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(message.sessionId),
        payload = message.payload.map(_.getUtf8Bytes),
        insertedTimestamp = message.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(message.primitive)
      ),
      reExtra = Some(reExtra)
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

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

  def traceInterfaceRequest(message: RestRequest, re: Re, reExtra: ReExtra, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    import StringUtils.Utf8String
    Util.logPayload(log, message.payload)
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = re.copy(
        insertedTimestamp = message.timestamp,
        payload = message.payload.map(_.getUtf8Bytes),
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
