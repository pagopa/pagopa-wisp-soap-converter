package eu.sia.pagopa.rendicontazioni.actor.rest

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException, RestException}
import eu.sia.pagopa.common.json.model.Error
import eu.sia.pagopa.common.json.model.rendicontazione._
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.rendicontazioni.actor.BaseInviaFlussoRendicontazioneActor
import eu.sia.pagopa.rendicontazioni.actor.soap.response.NodoInviaFlussoRendicontazioneResponse
import org.slf4j.MDC
import scalaxbmodel.nodoperpsp.NodoInviaFlussoRendicontazioneRisposta

import scala.concurrent.Future

final case class InviaFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
  extends BaseInviaFlussoRendicontazioneActor with NodoInviaFlussoRendicontazioneResponse {

  var req: RestRequest = _
  var replyTo: ActorRef = _

  override def receive: Receive = {
    case restRequest: RestRequest =>
      req = restRequest
      replyTo = sender()

      re = Some(
        Re(
          componente = Componente.FDR.toString,
          categoriaEvento = CategoriaEvento.INTERNO.toString,
          sessionId = Some(req.sessionId),
          payload = None,
          esito = Some(EsitoRE.CAMBIO_STATO.toString),
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN.toString,
          insertedTimestamp = restRequest.timestamp,
          erogatore = Some(FaultId.FDR),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.FDR)
        )
      )

      val pipeline = for {
        _ <- Future.successful(())

        nifrString <- translateInviaFlussoRendicontazioneRest2Soap(req.asInstanceOf[InviaFlussoRendicontazioneRequest])

        nodoInviaFlussoRendicontazione <- Future.fromTry(parseInput(nifrString, inputXsdValid))

        now = Util.now()
        re_ = Re(
          idDominio = Some(nodoInviaFlussoRendicontazione.identificativoDominio),
          psp = Some(nodoInviaFlussoRendicontazione.identificativoPSP),
          componente = Componente.FDR.toString,
          categoriaEvento = CategoriaEvento.INTERNO.toString,
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN.toString,
          fruitore = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
          erogatore = Some(FaultId.FDR),
          canale = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
          esito = Some(EsitoRE.RICEVUTA.toString),
          sessionId = Some(req.sessionId),
          insertedTimestamp = now,
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.FDR)
        )
        _ = re = Some(re_)

        (pa, psp, canale) <- Future.fromTry(checks(ddataMap, nodoInviaFlussoRendicontazione, false, actorClassId))
        _ = re = re.map(r => r.copy(fruitoreDescr = canale.flatMap(c => c.description), pspDescr = psp.flatMap(p => p.description)))

        (flussoRiversamento, flussoRiversamentoContent) <- validateRendicontazione(nodoInviaFlussoRendicontazione, checkUTF8, inputXsdValid)
        _ <- saveRendicontazione(nodoInviaFlussoRendicontazione, flussoRiversamentoContent, flussoRiversamento,pa, ddataMap, actorClassId)

        _ = log.info(FdrLogConstant.logGeneraPayload("nodoInviaFlussoRendicontazioneRisposta"))
        nodoInviaFlussoRisposta = NodoInviaFlussoRendicontazioneRisposta(None, "")
        _ = log.info(FdrLogConstant.logSintattico("nodoInviaFlussoRendicontazioneRisposta"))
        sr = RestResponse(req.sessionId, None, StatusCodes.OK.intValue, re, req.testCaseId, None)
      } yield sr

      pipeline.recover({
        case rex: RestException =>
          Future.successful(generateResponse(Some(rex)))
        case cause: Throwable =>
          val pmae = RestException(DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, cause)
          Future.successful(generateResponse(Some(pmae)))
      }) map (sr => {
        log.info(FdrLogConstant.logEnd(actorClassId))
        replyTo ! sr
        complete()
      })
  }

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, dpe, re)
  }

  def actorError(replyTo: ActorRef, req: RestRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    val dpa = RestException(dpe.getMessage, StatusCodes.InternalServerError.intValue, dpe)
    val response = makeFailureResponse(req.sessionId, req.testCaseId, dpa, re)
    replyTo ! response
  }

  private def makeFailureResponse(sessionId: String, tcid: Option[String], restException: RestException, re: Option[Re]): RestResponse = {
    import spray.json._
    log.error(restException, s"Errore generico: ${restException.message}")
    val err = Error(restException.message).toJson.toString()
    RestResponse(sessionId, Some(err), restException.statusCode, re, tcid, Some(restException))
  }

  private def generateResponse(exception: Option[RestException]) = {
    log.info(FdrLogConstant.logGeneraPayload(actorClassId + "Risposta"))
    val httpStatusCode = exception.map(_.statusCode).getOrElse(StatusCodes.OK.intValue)
    log.debug(s"Generazione risposta $httpStatusCode")
    RestResponse(req.sessionId, None, httpStatusCode, re, req.testCaseId, None)
  }

}
