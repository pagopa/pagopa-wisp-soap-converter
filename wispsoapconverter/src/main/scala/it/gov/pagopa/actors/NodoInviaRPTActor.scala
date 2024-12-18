package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.response.NodoInviaRPTResponse
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.enums._
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.{CosmosPrimitive, CosmosRepository}
import it.gov.pagopa.common.rpt.split.RptFlow
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.{Esito, EventCategory}
import it.gov.pagopa.config.Channel
import it.gov.pagopa.exception.{RptFaultBeanException, WorkflowExceptionErrorCodes}
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{IntestazionePPT, NodoInviaRPTRisposta}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import java.time.Instant
import java.util.Base64
import scala.concurrent.Future

case class NodoInviaRPTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor with NodoInviaRPTResponse with RptFlow with ReUtil {

  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  val idCanaleAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idCanaleAGID")
  val idPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idPspAGID")
  val idIntPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "intPspAGID")
  val uriAdapterEcommerce: String = context.system.settings.config.getString("adapterEcommerce.url")
  val recoverFuture: PartialFunction[Throwable, Future[SoapResponse]] = {
    case cfb: RptFaultBeanException =>
      MDC.put(Constant.MDCKey.PROCESS_OUTCOME, Esito.ERROR.toString)
      MDC.put(Constant.MDCKey.ERROR_LINE, cfb.digitPaException.getMessage)
      log.warn(s"Errore generico durante $actorClassId, message: [${cfb.getMessage}]")
      if (cfb.workflowErrorCode.isDefined && cfb.rptKey.isDefined) {
        cfb.workflowErrorCode.get match {
          case WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO =>
            val now = Util.now()
            (for {
              _ <- Future.successful(())
              _ = reCambioStato(WorkflowStatus.SEMANTIC_CHECK_FAILED.toString, now)
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
      MDC.put(Constant.MDCKey.PROCESS_OUTCOME, Esito.ERROR.toString)
      MDC.put(Constant.MDCKey.ERROR_LINE, dpaex.getMessage)
      log.warn(s"Errore generico durante $actorClassId, message: [${dpaex.getMessage}]")
      if (dpaex.code.equals(DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD) || dpaex.code.equals(DigitPaErrorCodes.PPT_SINTASSI_XSD)) {
        val reCambioStato = re.get.copy(status = Some(WorkflowStatus.SYNTAX_CHECK_FAILED.toString), insertedTimestamp = Util.now())
        reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
      }
      val resItems = errorHandler(RptFaultBeanException(dpaex))
      Future.successful(SoapResponse(req.sessionId, resItems._1, StatusCodes.OK.intValue, re, req.testCaseId))
    case cause: Throwable =>
      log.warn(cause, s"Errore generico durante $actorClassId, message: [${cause.getMessage}]")
      MDC.put(Constant.MDCKey.PROCESS_OUTCOME, Esito.ERROR.toString)
      MDC.put(Constant.MDCKey.ERROR_LINE, cause.getMessage)
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
          eventCategory = EventCategory.INTERNAL,
          sessionId = None,
          requestPayload = None,
          insertedTimestamp = soapRequest.timestamp,
          businessProcess = Some(actorClassId)
        )
      )
      reRequest = ReRequest(null, req.testCaseId, re.get, None)

      val pipeline = for {
        _ <- Future.successful(())

        _ = log.info(LogConstant.logSintattico(actorClassId))
        _ = log.debug("Check sintattici input req")
        (intestazionePPT, nodoInviaRPT) <- Future.fromTry(parseInput(soapRequest.payload, inputXsdValid))
        _ = log.debug("Input parserizzato correttamente")
        _ = rptKey = RPTKey(intestazionePPT.identificativoDominio, intestazionePPT.identificativoUnivocoVersamento, intestazionePPT.codiceContestoPagamento)

        _ = MDC.put(Constant.MDCKey.ID_DOMINIO, rptKey.idDominio)
        _ = MDC.put(Constant.MDCKey.IUV, rptKey.iuv)
        _ = MDC.put(Constant.MDCKey.CCP, rptKey.ccp)
        _ = log.debug("Parserizzazione RPT")
        (ctRPT: CtRichiestaPagamentoTelematico, _) <- Future.fromTry(parseRpt(nodoInviaRPT.rpt, inputXsdValid, checkUTF8))
        _ = log.debug("RPT parserizzato correttamente")

        _ = re = re.map(r =>
          r.copy(
            sessionId = Some(MDC.get(Constant.MDCKey.SESSION_ID)),
            psp = Some(nodoInviaRPT.identificativoPSP),
            channel = Some(nodoInviaRPT.identificativoCanale),
            domainId = Some(intestazionePPT.identificativoDominio),
            ccp = Some(intestazionePPT.codiceContestoPagamento),
            iuv = Some(intestazionePPT.identificativoUnivocoVersamento),
            station = Some(intestazionePPT.identificativoStazioneIntermediarioPA)
          )
        )

        _ = reCambioStato(WorkflowStatus.SYNTAX_CHECK_PASSED.toString, Util.now())

        _ = log.debug("Validazione input")
        _ = log.info(LogConstant.logSemantico(actorClassId))
        idCanale = nodoInviaRPT.identificativoCanale
        isAGID =
          idCanale == idCanaleAgid && nodoInviaRPT.identificativoPSP == idPspAgid && nodoInviaRPT.identificativoIntermediarioPSP == idIntPspAgid
        // nel flusso nmu possono arrivare rpt simili appIo, cioè che presentano PO come metodo pagamento
        // flusso nmu non ha il bollo digitale
        isAppIO = isAGID && ctRPT.datiVersamento.tipoVersamento == scalaxbmodel.paginf.POValue
        pspOpt <-
          if (isAppIO) {
            Future.fromTry(DDataChecks.checkPsp(log, ddataMap, nodoInviaRPT.identificativoPSP).map(p => Some(p)))
          } else {
            Future.fromTry(validateInput(ddataMap, rptKey, nodoInviaRPT, intestazionePPT, ctRPT))
          }
        psp = pspOpt.get
        _ = canale = ddataMap.channels(idCanale)
        isBolloEnabled = psp.digitalStamp && canale.digitalStamp

        _ = log.debug("Validazione RPT")
        _ <- Future.fromTry(validateRpt(ddataMap, rptKey, nodoInviaRPT, intestazionePPT, ctRPT, isBolloEnabled))
        _ = log.debug("Controllo semantico Email RPT")
        _ <- Future.fromTry(checkEmail(ctRPT))
        _ = log.debug("Check semantici")

        _ = log.debug("Recupero parametri per request")
        idStazione = intestazionePPT.identificativoStazioneIntermediarioPA
        stazione = DDataChecks.getStation(ddataMap, idStazione)

        modelloPagamento: String = canale.paymentModel

        modelloUno =
          modelloPagamento == ModelloPagamento.IMMEDIATO.toString || modelloPagamento == ModelloPagamento.IMMEDIATO_MULTIBENEFICIARIO.toString

        (payloadNodoInviaRPTRisposta, _) <- manageNormal(intestazionePPT, modelloUno, isAGID)

      } yield SoapResponse(soapRequest.sessionId, payloadNodoInviaRPTRisposta, StatusCodes.OK.intValue, re, soapRequest.testCaseId)

      pipeline
        .recoverWith(recoverFuture)
        .map(soapResponse => {
          traceWebserviceInvocation(soapRequest, soapResponse, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
          log.info(LogConstant.logEnd(actorClassId))
          replyTo ! soapResponse
          complete()
        })
  }

  private def manageNormal(
                            intestazionePPT: IntestazionePPT,
                            modelloUno: Boolean,
                            isAGID: Boolean
                          ): Future[(String, NodoInviaRPTRisposta)] = {
    for {
      _ <- Future.successful(())
      _ = log.debug("Salvataggio messaggio su Cosmos")
      _ = insertTime = Util.now()
      _ <- saveData(intestazionePPT, updateTokenItem = false)
      _ = if (isAGID) {
        reCambioStato(WorkflowStatus.SEMANTIC_CHECK_PASSED.toString, Util.now())
      }

      _ = log.debug("Costruzione msg input resp")
      _ = log.info(LogConstant.logGeneraPayload("nodoInviaRPTRisposta"))
      url = RPTUtil.getAdapterEcommerceUri(uriAdapterEcommerce, req)
      (payloadNodoInviaRPTRisposta, nodoInviaRPTRisposta) <- Future.fromTry(
        createNodoInviaRPTRisposta(outputXsdValid, Some(url), if (modelloUno) Some(1) else Some(0), esitoResponse = true, None)
      )
    } yield (payloadNodoInviaRPTRisposta, nodoInviaRPTRisposta)
  }

  def saveData(intestazionePPT: IntestazionePPT, updateTokenItem: Boolean): Future[String] = {
    log.debug("Salvataggio messaggio RPT")
    val id = req.sessionId
    val zipped = Util.zipContent(req.payload)
    cosmosRepository.save(CosmosPrimitive(re.get.insertedTimestamp.toString.substring(0, 10), id, actorClassId, Base64.getEncoder.encodeToString(zipped)))
    Future.successful(id)
  }

  def reCambioStato(stato: String, time: Instant, tipo: Option[String] = None): Unit = {
    reEventFunc(
      ReRequest(req.sessionId, req.testCaseId, re.get.copy(status = Some(s"${tipo.getOrElse("")}${stato}"), insertedTimestamp = time), None),
      log,
      ddataMap
    )
  }

}
