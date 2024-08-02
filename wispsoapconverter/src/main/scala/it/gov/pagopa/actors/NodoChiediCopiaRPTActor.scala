package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.response.NodoChiediCopiaRPTResponse
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.enums._
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.{CosmosPrimitive, CosmosRepository}
import it.gov.pagopa.common.rpt.split.RptFlow
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.{CategoriaEvento, Componente, Esito, SottoTipoEvento}
import it.gov.pagopa.config.Channel
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{IntestazionePPT, NodoChiediCopiaRTRisposta}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import java.time.Instant
import java.util.Base64
import scala.concurrent.Future

case class NodoChiediCopiaRPTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor with NodoChiediCopiaRPTResponse with RptFlow with ReUtil {

  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  val idCanaleAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idCanaleAGID")
  val idPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idPspAGID")
  val idIntPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "intPspAGID")
  val uriAdapterEcommerce: String = context.system.settings.config.getString("adapterEcommerce.url")
  val recoverFuture: PartialFunction[Throwable, Future[SoapResponse]] = {
    case cfb: RptFaultBeanException =>
      log.warn(s"Errore generico durante $actorClassId, message: [${cfb.getMessage}]")
      if (cfb.workflowErrorCode.isDefined && cfb.rptKey.isDefined) {
        cfb.workflowErrorCode.get match {
          case WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO =>
            val now = Util.now()
            (for {
              _ <- Future.successful(())
              _ = reCambioStato(StatoRPTEnum.RPT_RIFIUTATA_NODO.toString, now)
              resItems = errorHandler(cfb)
              res = SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId)
            } yield res) recoverWith recoverGenericError
          case a =>
            log.warn(s"workflow error code non gestito [${a.toString}]")
            val cfb = RptFaultBeanException(exception.DigitPaException(s"workflow error code non gestito [${a.toString}]", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
            val resItems = errorHandler(cfb)
            Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
        }
      } else {
        val resItems = errorHandler(cfb)
        Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
      }
    case dpaex: DigitPaException =>
      log.warn(s"Errore generico durante $actorClassId, message: [${dpaex.getMessage}]")
      val resItems = errorHandler(RptFaultBeanException(dpaex))
      Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
    case cause: Throwable =>
      log.warn(cause, s"Errore generico durante $actorClassId, message: [${cause.getMessage}]")
      val cfb = RptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, cause))
      val resItems = errorHandler(cfb)
      Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
  }
  val recoverGenericError: PartialFunction[Throwable, Future[SoapResponse]] = {
    case originalException: Throwable =>
      log.debug(s"Errore generico durante $actorClassId, message: [${originalException.getMessage}]")
      val cfb = RptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, originalException))
      val resItems = errorHandler(cfb)
      Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
  }
  var req: SoapRequest = _
  var replyTo: ActorRef = _
  var rptKey: RPTKey = _
  var reRequest: ReRequest = _
  var insertTime: Instant = _
  var canale: Channel = _
  var re: Option[Re] = None

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, dpe, re)
  }

  override def receive: Receive = {
    case soapRequest: SoapRequest =>
      req = soapRequest
      replyTo = sender()

      // parachute session id, will be replaced lately when the sessionId will be generated
      MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)

      re = Some(
        Re(
          componente = Componente.WISP_SOAP_CONVERTER,
          categoriaEvento = CategoriaEvento.INTERNAL,
          sessionId = None,
          sessionIdOriginal = Some(req.sessionId),
          payload = None,
          esito = Esito.EXCECUTED_INTERNAL_STEP,
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN,
          insertedTimestamp = soapRequest.timestamp,
          erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
        )
      )
      reRequest = ReRequest(null, req.testCaseId, re.get, None)
      MDC.put(Constant.MDCKey.ORIGINAL_SESSION_ID, req.sessionId)

      val pipeline = for {
        _ <- Future.successful(())

        _ = log.info(LogConstant.logSintattico(actorClassId))
        _ = log.debug("Check sintattici input req")
        (nodoChiediCopiaRT) <- Future.fromTry(parseInput(soapRequest.payload, inputXsdValid))
        _ = log.debug("Input parserizzato correttamente")
        _ = MDC.put(Constant.MDCKey.SESSION_ID, RPTUtil.getUniqueKey(req, intestazionePPT));
        _ = MDC.put(Constant.MDCKey.ID_DOMINIO, nodoChiediCopiaRT.identificativoDominio)
        _ = MDC.put(Constant.MDCKey.INTERMEDIARIO, nodoChiediCopiaRT.identificativoIntermediarioPA)
        _ = MDC.put(Constant.MDCKey.STAZIONE, nodoChiediCopiaRT.identificativoStazioneIntermediarioPA)
        _ = MDC.put(Constant.MDCKey.IUV, nodoChiediCopiaRT.identificativoUnivocoVersamento)
        _ = MDC.put(Constant.MDCKey.CCP, nodoChiediCopiaRT.codiceContestoPagamento)
        _ = log.debug("Parserizzazione RPT")
        (ctRPT: CtRichiestaPagamentoTelematico, _) <- Future.fromTry(parseRpt(nodoChiediCopiaRT.rpt, inputXsdValid, checkUTF8))
        _ = log.debug("RPT parserizzato correttamente")

        _ = re = re.map(r =>
          r.copy(
            sessionId = Some(MDC.get(Constant.MDCKey.SESSION_ID)),
            fruitore = Some(nodoChiediCopiaRT.identificativoStazioneIntermediarioPA),
            fruitoreDescr = Some(nodoChiediCopiaRT.identificativoStazioneIntermediarioPA),
            idDominio = Some(nodoChiediCopiaRT.identificativoDominio),
            ccp = Some(nodoChiediCopiaRT.codiceContestoPagamento),
            iuv = Some(nodoChiediCopiaRT.identificativoUnivocoVersamento),
            stazione = Some(nodoChiediCopiaRT.identificativoStazioneIntermediarioPA),
          )
        )

        _ = reCambioStato(StatoRPTEnum.RPT_RICEVUTA_NODO.toString, Util.now())

        _ = log.debug("Recupero parametri per request")
        idStazione = nodoChiediCopiaRT.identificativoStazioneIntermediarioPA
        stazione = DDataChecks.getStation(ddataMap, idStazione)

        modelloPagamento: String = canale.paymentModel

        modelloUno =
          modelloPagamento == ModelloPagamento.IMMEDIATO.toString || modelloPagamento == ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO.toString

        _ = re = re.map(r => r.copy(pspDescr = psp.description, fruitoreDescr = Some(stazione.stationCode)))

        (payloadNodoChiediCopiaRPTRisposta, _) <- manageNormal(intestazionePPT, modelloUno, isAGID)

      } yield SoapResponse(soapRequest.sessionId, payloadNodoChiediCopiaRPTRisposta, StatusCodes.OK.intValue, re, soapRequest.testCaseId)

      pipeline
        .recoverWith(recoverFuture)
        .map(sr2 => {
          traceInterfaceRequest(soapRequest, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
          log.info(LogConstant.logEnd(actorClassId))
          replyTo ! sr2
          complete()
        })
  }

  private def manageNormal(
                            intestazionePPT: IntestazionePPT,
                            modelloUno: Boolean,
                            isAGID: Boolean
                          ): Future[(String, NodoChiediCopiaRPTRisposta)] = {
    for {
      _ <- Future.successful(())
      _ = log.debug("Salvataggio messaggio su Cosmos")
      _ = insertTime = Util.now()
      _ <- saveData(intestazionePPT, updateTokenItem = false)
      _ = if (isAGID) {
        reCambioStato(StatoRPTEnum.RPT_ACCETTATA_NODO.toString, Util.now())
      } else {
        reCambioStato(StatoRPTEnum.RPT_PARCHEGGIATA_NODO.toString, Util.now())
      }

      _ = log.debug("Costruzione msg input resp")
      _ = log.info(LogConstant.logGeneraPayload("nodoChiediCopiaRPTRisposta"))
      url = RPTUtil.getAdapterEcommerceUri(uriAdapterEcommerce, req, intestazionePPT)
      (payloadNodoChiediCopiaRPTRisposta, nodoChiediCopiaRPTRisposta) <- Future.fromTry(
        createNodoChiediCopiaRPTRisposta(outputXsdValid, Some(url), if (modelloUno) Some(1) else Some(0), esitoResponse = true, None)
      )
    } yield (payloadNodoChiediCopiaRPTRisposta, nodoChiediCopiaRPTRisposta)
  }

  def saveData(intestazionePPT: IntestazionePPT, updateTokenItem: Boolean): Future[String] = {
    log.debug("Salvataggio messaggio RPT")
    val id = RPTUtil.getUniqueKey(req, intestazionePPT)
    val zipped = Util.zipContent(req.payload)
    cosmosRepository.save(CosmosPrimitive(re.get.insertedTimestamp.toString.substring(0, 10), id, actorClassId, Base64.getEncoder.encodeToString(zipped)))
    Future.successful(id)
  }

  def reCambioStato(stato: String, time: Instant, tipo: Option[String] = None): Unit = {
    reEventFunc(
      ReRequest(req.sessionId, req.testCaseId, re.get.copy(status = Some(s"${tipo.getOrElse("")}${stato}"), insertedTimestamp = time, esito = Esito.EXCECUTED_INTERNAL_STEP), None),
      log,
      ddataMap
    )
  }

}
