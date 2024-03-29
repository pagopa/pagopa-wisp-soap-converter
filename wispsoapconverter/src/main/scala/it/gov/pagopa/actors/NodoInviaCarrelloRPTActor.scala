package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import com.nimbusds.jose.util.StandardCharset
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
import it.gov.pagopa.common.util.xml.XsdValid
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.exception.{CarrelloRptFaultBeanException, WorkflowExceptionErrorCodes}
import org.slf4j.MDC
import scalaxbmodel.nodoperpa.{IntestazioneCarrelloPPT, NodoInviaCarrelloRPT, NodoInviaCarrelloRPTRisposta}
import scalaxbmodel.paginf.CtRichiestaPagamentoTelematico

import java.time.LocalDateTime
import java.util.Base64
import scala.concurrent.Future
import scala.util.{Failure, Try}
case class NodoInviaCarrelloRPTActorPerRequest(cosmosRepository:CosmosRepository,actorProps: ActorProps) extends PerRequestActor with NodoInviaCarrelloRPTResponse with CarrelloFlow with ReUtil {

  var req: SoapRequest = _
  var replyTo: ActorRef = _

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

  var idCanale: String = _
  var idCarrello: String = _
  var rptKeys: Seq[RPTKey] = _
  var reRequest: ReRequest = _
  var re: Option[Re] = None
  var insertTime: LocalDateTime = _

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, Option(idCanale), dpe, Option(idCarrello), Option(rptKeys), re)
  }

  def saveCarrello(
      now: LocalDateTime,
      intestazioneCarrelloPPT: IntestazioneCarrelloPPT,
  ): Future[Int] = {
    log.debug("Salvataggio messaggio Carrello")
    val id = RPTUtil.getUniqueKey(req,intestazioneCarrelloPPT)
    val zipped = Util.zipContent(req.payload.getBytes(StandardCharset.UTF_8))
    cosmosRepository.save(CosmosPrimitive(re.get.insertedTimestamp.toString.substring(0,10),id,actorClassId,Base64.getEncoder.encodeToString(zipped)))
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
      _ = re = re.map(r => r.copy(pspDescr = psp.description))
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
          _ <- saveCarrello(insertTime,intestazioneCarrelloPPT)
          now = Util.now()
          _ ={
            val reCambioStato = re.get.copy(status = Some(StatoCarrelloEnum.CART_ACCETTATO_NODO.toString), insertedTimestamp = now)
            reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
            rpts.map(rpt=>{
              val reCambioStatorpt = re.get.copy(
                iuv = Some(rpt.datiVersamento.identificativoUnivocoVersamento),
                ccp = Some(rpt.datiVersamento.codiceContestoPagamento),
                idDominio = Some(rpt.dominio.identificativoDominio),
                status = Some(StatoRPTEnum.RPT_ACCETTATA_NODO.toString),
                insertedTimestamp = now
              )
              reEventFunc(reRequest.copy(re = reCambioStatorpt), log, ddataMap)
            })
          }
          ctRPT = rpts.head
          _ = log.debug("Check email")
          _ <- Future.fromTry(checkEmail(ctRPT))
          _ = if(isAGID){
            val now = Util.now()
            val reCambioStato = re.get.copy(status = Some(StatoCarrelloEnum.CART_PARCHEGGIATO_NODO.toString), insertedTimestamp = now)
            reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
            rpts.map(rpt=>{
              val reCambioStatorpt = re.get.copy(
                iuv = Some(rpt.datiVersamento.identificativoUnivocoVersamento),
                ccp = Some(rpt.datiVersamento.codiceContestoPagamento),
                idDominio = Some(rpt.dominio.identificativoDominio),
                status = Some(StatoRPTEnum.RPT_PARCHEGGIATA_NODO.toString),
                insertedTimestamp = now
              )
              reEventFunc(reRequest.copy(re = reCambioStatorpt), log, ddataMap)
            })
          }
          url = RPTUtil.getAdapterEcommerceUri(uriAdapterEcommerce,req,intestazioneCarrelloPPT)
          (payloadNodoInviaCarrelloRPTRisposta, nodoInviaCarrelloRPTRisposta) <- Future.fromTry(
            createNodoInviaCarrelloRPTRisposta(Some(url), esitoResponse = true, nodoInviaCarrelloRPT.identificativoCanale, None)
          )

        } yield (payloadNodoInviaCarrelloRPTRisposta, nodoInviaCarrelloRPTRisposta)
      }
    } yield SoapResponse(req.sessionId, payloadNodoInviaCarrelloRPTRisposta, StatusCodes.OK.intValue, re, req.testCaseId)
  }

  override def receive: Receive = { case soapRequest: SoapRequest =>
    req = soapRequest
    replyTo = sender()

    re = Some(
      Re(
        componente = Componente.FESP.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        sessionId = Some(req.sessionId),
        sessionIdOriginal = Some(req.sessionId),
        payload = None,
        esito = Some(EsitoRE.CAMBIO_STATO.toString),
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        insertedTimestamp = soapRequest.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
      )
    )
    reRequest = ReRequest(req.sessionId, req.testCaseId, re.get, None)

    MDC.put(Constant.MDCKey.ORIGINAL_SESSION_ID, req.sessionId)

    val pipeline = for {
      _ <- Future.successful(())

      _ = log.info(LogConstant.logSintattico(actorClassId))
      _ = log.debug("Check sintattici input")
      (intestazioneCarrelloPPT, nodoInviaCarrelloRPT) <- Future.fromTry(parseCarrello(soapRequest.payload, inputXsdValid))
      _ = idCarrello = intestazioneCarrelloPPT.identificativoCarrello
      _ = re = re.map(r =>
        r.copy(
          psp = Some(nodoInviaCarrelloRPT.identificativoPSP),
          canale = Some(nodoInviaCarrelloRPT.identificativoCanale),
          fruitore = Some(intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA),
          fruitoreDescr = Some(intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA),
          stazione = Some(intestazioneCarrelloPPT.identificativoStazioneIntermediarioPA)
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
      reCambioStato = re.get.copy(status = Some(StatoCarrelloEnum.CART_RICEVUTO_NODO.toString), insertedTimestamp = now)
      _ = reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
      _ = log.info(LogConstant.logSemantico(actorClassId))
      soapResponse <- manageCarrello(nodoInviaCarrelloRPT, intestazioneCarrelloPPT, rpts)

    } yield soapResponse

    pipeline
      .recoverWith(recoverPipeline)
      .map(sr => {
        traceInterfaceRequest(soapRequest, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
        log.info(LogConstant.logEnd(actorClassId))
        replyTo ! sr
        complete()
      })
  }

  val recoverGenericError: PartialFunction[Throwable, Future[SoapResponse]] = { case originalException: Throwable =>
    log.debug(s"Errore generico durante InviaCarrelloRPT, message: [${originalException.getMessage}]")
    val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, originalException), idCanale = None)
    Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
  }

  val recoverPipeline: PartialFunction[Throwable, Future[SoapResponse]] = {
    case cfb: CarrelloRptFaultBeanException =>
      if (cfb.workflowErrorCode.isDefined && cfb.idCarrello.isDefined && cfb.rptKeys.isDefined) {
        cfb.workflowErrorCode.get match {
          case WorkflowExceptionErrorCodes.CARRELLO_ERRORE_SEMANTICO | WorkflowExceptionErrorCodes.RPT_ERRORE_SEMANTICO =>
            (for {
              _ <- Future.successful(())
              now = Util.now()
              reCambioStato = re.get.copy(status = Some(StatoCarrelloEnum.CART_RIFIUTATO_NODO.toString), insertedTimestamp = now)
              _ = reEventFunc(reRequest.copy(re = reCambioStato), log, ddataMap)
              res = errorHandler(req.sessionId, req.testCaseId, cfb, re)
            } yield res) recoverWith recoverGenericError

          case _ =>
            val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR), idCanale = None)
            Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
        }
      } else {
        Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
      }
    case e: Throwable =>
      log.warn(e, e.getMessage)
      val cfb = CarrelloRptFaultBeanException(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), idCanale = None)
      Future.successful(errorHandler(req.sessionId, req.testCaseId, cfb, re))
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
