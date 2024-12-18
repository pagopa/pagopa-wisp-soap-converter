package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.flow.CarrelloFlow
import it.gov.pagopa.actors.response.NodoInviaCarrelloRPTResponse
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.enums._
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.{CosmosPrimitive, CosmosRepository}
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.{Esito, EventCategory}
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.exception.{CarrelloRptFaultBeanException, WorkflowExceptionErrorCodes}
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, NodoInviaCarrelloRPT, NodoInviaCarrelloRPTRisposta}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import java.time.Instant
import java.util.Base64
import scala.concurrent.Future
import scala.util.{Failure, Try}

case class NodoInviaCarrelloRPTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor with NodoInviaCarrelloRPTResponse with CarrelloFlow with ReUtil {

  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  val idCanaleAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idCanaleAGID")
  val idPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "idPspAGID")
  val idIntPspAgid: String = DDataChecks.getConfigurationKeys(ddataMap, "intPspAGID")
  val uriAdapterEcommerce: String = context.system.settings.config.getString("adapterEcommerce.url")
  val maxNumRptInCart: Int =
    DDataChecks.getConfigurationKeys(ddataMap, "inviaCarrelloRpt.maxNumRptInCart", "rpt-invia").toInt
  val maxNumRptInCartMulti: Int =
    DDataChecks.getConfigurationKeys(ddataMap, "inviaCarrelloRpt.maxNumRptInCartMultibeneficiario").toInt
  val maxVersamentiInSecondRptMulti: Int =
    DDataChecks.getConfigurationKeys(ddataMap, "inviaCarrelloRpt.maxVersamentiInSecondRptMultibeneficiario").toInt
  val recoverGenericError: PartialFunction[Throwable, Future[SoapResponse]] = {
    case originalException: Throwable =>
      log.debug(s"Errore generico durante InviaCarrelloRPT, message: [${originalException.getMessage}]")
      val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, originalException), idCanale = None)
      Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
  }
  val recoverPipeline: PartialFunction[Throwable, Future[SoapResponse]] = {
    case cfb: CarrelloRptFaultBeanException =>
      MDC.put(Constant.MDCKey.PROCESS_OUTCOME, Esito.ERROR.toString)
      MDC.put(Constant.MDCKey.ERROR_LINE, cfb.digitPaException.getMessage)
      val exceptionCode = cfb.digitPaException.code
      if (cfb.workflowErrorCode.isDefined && cfb.idCarrello.isDefined && cfb.rptKeys.isDefined) {
        cfb.workflowErrorCode.get match {
          case WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO | WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO =>
            (for {
              _ <- Future.successful(())
              now = Util.now()
              reCambioStato = re.get.copy(status = Some(WorkflowStatus.SEMANTIC_CHECK_FAILED.toString), insertedTimestamp = now)
              _ = reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
              res = errorHandler(req.sessionId, req.testCaseId, cfb, re)
            } yield res) recoverWith recoverGenericError

          case _ =>
            if (exceptionCode.equals(DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD) || exceptionCode.equals(DigitPaErrorCodes.PPT_SINTASSI_XSD)) {
              val reCambioStato = re.get.copy(status = Some(WorkflowStatus.SYNTAX_CHECK_FAILED.toString), insertedTimestamp = Util.now())
              reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
            }
            val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR), idCanale = None)
            Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
        }
      } else {
        if (exceptionCode.equals(DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD) || exceptionCode.equals(DigitPaErrorCodes.PPT_SINTASSI_XSD)) {
          val reCambioStato = re.get.copy(status = Some(WorkflowStatus.SYNTAX_CHECK_FAILED.toString), insertedTimestamp = Util.now())
          reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
        }
        Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
      }
    case e: Throwable =>
      log.warn(e, e.getMessage)
      MDC.put(Constant.MDCKey.PROCESS_OUTCOME, Esito.ERROR.toString)
      MDC.put(Constant.MDCKey.ERROR_LINE, e.getMessage)
      val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), idCanale = None)
      Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
  }
  var req: SoapRequest = _
  var replyTo: ActorRef = _
  var idCanale: String = _
  var idCarrello: String = _
  var rptKeys: Seq[RPTKey] = _
  var reRequest: ReRequest = _
  var re: Option[Re] = None
  var insertTime: Instant = _

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, Option(idCanale), dpe, Option(idCarrello), Option(rptKeys), re)
  }

  override def receive: Receive = {
    case soapRequest: SoapRequest =>
      req = soapRequest
      replyTo = sender()

      // parachute session id, will be replaced lately when the sessionId will be generated
      MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)

      re = Some(
        Re(
          sessionId = Some(MDC.get(Constant.MDCKey.SESSION_ID)),
          eventCategory = EventCategory.INTERNAL,
          requestPayload = None,
          insertedTimestamp = soapRequest.timestamp,
          businessProcess = Some(actorClassId),
        )
      )
      reRequest = ReRequest(null, req.testCaseId, re.get, None)

      val pipeline = for {
        _ <- Future.successful(())

        _ = log.info(LogConstant.logSintattico(actorClassId))
        _ = log.debug("Check sintattici input")
        (intestazioneCarrelloPPT, nodoInviaCarrelloRPT) <- Future.fromTry(parseCarrello(soapRequest.payload, inputXsdValid))
        _ = idCarrello = intestazioneCarrelloPPT.identificativoCarrello
        _ = re = re.map(r =>
          r.copy(
            cartId = Some(idCarrello),
            psp = Some(nodoInviaCarrelloRPT.identificativoPSP),
            channel = Some(nodoInviaCarrelloRPT.identificativoCanale),
            station = Some(intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA)
          )
        )
        _ = MDC.put(Constant.MDCKey.ID_CARRELLO, intestazioneCarrelloPPT.identificativoCarrello)
        _ = log.debug("Check sintattici input rpts")
        _ = rptKeys = nodoInviaCarrelloRPT.listaRPT.elementoListaRPT.map(e => RPTKey(e.identificativoDominio, e.identificativoUnivocoVersamento, e.codiceContestoPagamento))

        rpts <- Future.fromTry(
          parseRpts(nodoInviaCarrelloRPT.identificativoCanale, inputXsdValid, nodoInviaCarrelloRPT.listaRPT.elementoListaRPT, intestazioneCarrelloPPT.identificativoCarrello, rptKeys, checkUTF8)
        )
        _ = log.debug("Salvataggio stato log CARRELLO_RICEVUTA_NODO/RPT_RICEVUTA_NODO")

        now = Util.now()
        reCambioStato = re.get.copy(status = Some(WorkflowStatus.SYNTAX_CHECK_PASSED.toString), insertedTimestamp = now)
        _ = reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
        _ = log.info(LogConstant.logSemantico(actorClassId))
        soapResponse <- manageCarrello(nodoInviaCarrelloRPT, intestazioneCarrelloPPT, rpts)

      } yield soapResponse

      pipeline
        .recoverWith(recoverPipeline)
        .map(soapResponse => {
          traceWebserviceInvocation(soapRequest, soapResponse, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
          log.info(LogConstant.logEnd(actorClassId))
          replyTo ! soapResponse
          complete()
        })
  }

  def manageCarrello(nodoInviaCarrelloRPT: NodoInviaCarrelloRPT, intestazioneCarrelloPPT: IntestazioneCarrelloPPT, rpts: Seq[CtRichiestaPagamentoTelematico]): Future[SoapResponse] = {
    log.debug("Controllo validità Carrello")

    val isAGID = nodoInviaCarrelloRPT.identificativoCanale == idCanaleAgid &&
      nodoInviaCarrelloRPT.identificativoPSP == idPspAgid &&
      nodoInviaCarrelloRPT.identificativoIntermediarioPSP == idIntPspAgid

    idCanale = nodoInviaCarrelloRPT.identificativoCanale

    for {
      _ <- Future.successful(())
      multibeneficiario = nodoInviaCarrelloRPT.multiBeneficiario.contains(true)
      (brokerpa, station) <- Future.fromTry(
        validCarrello(ddataMap, maxNumRptInCart, rptKeys, intestazioneCarrelloPPT, nodoInviaCarrelloRPT, multibeneficiario, idCanaleAgid, idPspAgid, idIntPspAgid)
      )
      _ <-
        if (isAGID && multibeneficiario) {
          for {
            (paFiscalCode, noticeNumber) <- Future.fromTry(validCarrelloMultibeneficiario(ddataMap, maxNumRptInCartMulti, intestazioneCarrelloPPT, nodoInviaCarrelloRPT))
            _ = re = re.map(_.copy(noticeNumber = Some(noticeNumber)))
          } yield Some((paFiscalCode, noticeNumber, brokerpa, station))
        } else {
          Future.successful(None)
        }

      psp <- Future.fromTry(DDataChecks.checkPsp(log, ddataMap, nodoInviaCarrelloRPT.identificativoPSP))
      canale = ddataMap.channels(idCanale)
      isBolloEnabled = psp.digitalStamp && canale.digitalStamp

      _ = log.debug("Controllo validità Rpt")
      _ <- Future.fromTry(validRpts(ddataMap, intestazioneCarrelloPPT.identificativoCarrello, rptKeys, nodoInviaCarrelloRPT, intestazioneCarrelloPPT, isBolloEnabled, maxVersamentiInSecondRptMulti))
      _ = log.debug("Recupero parametri per request")
      (payloadNodoInviaCarrelloRPTRisposta, _) <- {
        for {
          _ <- Future.successful(())

          _ = log.debug("Salvataggio carrello")
          _ = insertTime = Util.now()
          _ <- saveCarrello(insertTime, intestazioneCarrelloPPT)
          now = Util.now()
          _ = {
            rpts.map(rpt => {
              val reCambioStatorpt = re.get.copy(
                iuv = Some(rpt.datiVersamento.identificativoUnivocoVersamento),
                ccp = Some(rpt.datiVersamento.codiceContestoPagamento),
                domainId = Some(rpt.dominio.identificativoDominio),
                status = Some(WorkflowStatus.SEMANTIC_CHECK_PASSED.toString),
                insertedTimestamp = now
              )
              reEventFunc(reRequest.copy(re = reCambioStatorpt), log, ddataMap)
            })
          }
          ctRPT = rpts.head
          _ = log.debug("Check email")
          _ <- Future.fromTry(checkEmail(ctRPT))
          url = RPTUtil.getAdapterEcommerceUri(uriAdapterEcommerce, req)
          (payloadNodoInviaCarrelloRPTRisposta, nodoInviaCarrelloRPTRisposta) <- Future.fromTry(
            createNodoInviaCarrelloRPTRisposta(Some(url), esitoResponse = true, nodoInviaCarrelloRPT.identificativoCanale, None)
          )

        } yield (payloadNodoInviaCarrelloRPTRisposta, nodoInviaCarrelloRPTRisposta)
      }
    } yield SoapResponse(req.sessionId, payloadNodoInviaCarrelloRPTRisposta, StatusCodes.OK.intValue, re, req.testCaseId)
  }

  def saveCarrello(
                    now: Instant,
                    intestazioneCarrelloPPT: IntestazioneCarrelloPPT,
                  ): Future[Int] = {
    log.debug("Salvataggio messaggio Carrello")
    val id = req.sessionId
    val zipped = Util.zipContent(req.payload)
    cosmosRepository.save(CosmosPrimitive(re.get.insertedTimestamp.toString.substring(0, 10), id, actorClassId, Base64.getEncoder.encodeToString(zipped)))
  }

  def createNodoInviaCarrelloRPTRisposta(
                                          url: Option[String],
                                          esitoResponse: Boolean,
                                          idCanale: String,
                                          carrelloRptFaultBeanException: Option[CarrelloRptFaultBeanException]
                                        ): Try[(String, NodoInviaCarrelloRPTRisposta)] = {
    val esitoComplessivoOperazione = if (esitoResponse) Constant.OK else Constant.KO
    val urlChecked = if (esitoResponse) url else None
    val inviaCarrelloRPTRisposta =
      generateNodoInviaCarrelloRPTRisposta(urlChecked, carrelloRptFaultBeanException, esitoComplessivoOperazione)

    (for {
      payloadInviaCarrelloRPTRisposta <- XmlEnum.nodoInviaCarrelloRPTRisposta2Str_nodoperpa(inviaCarrelloRPTRisposta)
      _ <- XsdValid.checkOnly(payloadInviaCarrelloRPTRisposta, XmlEnum.NODO_INVIA_CARRELLO_RPT_RISPOSTA_NODOPERPA, outputXsdValid)
    } yield (payloadInviaCarrelloRPTRisposta, inviaCarrelloRPTRisposta)).recoverWith({ case e =>
      log.warn(RptHelperLog.XSD_KO(e.getMessage))
      val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), idCanale = Some(idCanale))
      Failure(cfb)
    })

  }

}
