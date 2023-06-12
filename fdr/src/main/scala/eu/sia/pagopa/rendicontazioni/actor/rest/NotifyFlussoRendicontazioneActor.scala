package eu.sia.pagopa.rendicontazioni.actor.rest

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.HttpFdrServiceManagement
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException, RestException}
import eu.sia.pagopa.common.json.model.Error
import eu.sia.pagopa.common.json.model.rendicontazione.{NotifyFlowRequest, SenderTypeEnum}
import eu.sia.pagopa.common.json.{JsonEnum, JsonValid}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.DDataChecks.checkPsp
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.BaseFlussiRendicontazioneActor
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import org.slf4j.MDC
import scalaxbmodel.flussoriversamento.{CtFlussoRiversamento, CtIdentificativoUnivoco, CtIdentificativoUnivocoPersonaG, CtIstitutoMittente, CtIstitutoRicevente, Number1u461}
import spray.json._

import javax.xml.datatype.DatatypeFactory
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

final case class NotifyFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
  extends BaseFlussiRendicontazioneActor with ReUtil {

  var req: RestRequest = _
  var replyTo: ActorRef = _

  var _psp: String = _
  var _fdr: String = _
  var _rev: Integer = _
  var _retry: Integer = _

  override def receive: Receive = {
    case restRequest: RestRequest =>
      replyTo = sender()
      req = restRequest

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
          erogatore = Some(FaultId.FDRNODO),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.FDRNODO),
          fruitore = Some(Componente.FDR_NOTIFIER.toString)
        )
      )

      (for {
        _ <- Future.successful(())
        _ = log.info(FdrLogConstant.logSintattico(actorClassId))
        _ <- Future.fromTry(parseInput(req))
      } yield RestResponse(req.sessionId, None, StatusCodes.OK.intValue, re, req.testCaseId, None) )
        .recoverWith({
          case rex: RestException =>
            Future.successful(generateResponse(Some(rex)))
          case cause: Throwable =>
            val pmae = RestException(DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, cause)
            Future.successful(generateResponse(Some(pmae)))
      }).map( res => {
        traceInterfaceRequest(req, re.get, req.reExtra, reEventFunc, ddataMap)
        replyTo ! res
      }).flatMap(_ => {
        Future.sequence(
          //TODO fare le chiamate verso FDR
          Seq(Future.successful(log.info("Gestione asincrona"))) ++
          Seq(for {
            retrieveFlow <- HttpFdrServiceManagement.retrieveFlow(req.sessionId, req.testCaseId, "retrieveFlow", Componente.FDR.toString, _fdr, _rev.toString, _psp, actorProps, re.get)

            _ = log.info(FdrLogConstant.logGeneraPayload(s"nodoInviaFlussoRendicontazione SOAP"))

            flussoRiversamento = CtFlussoRiversamento(
              Number1u461,
              retrieveFlow.reportingFlowName,
              DatatypeFactory.newInstance().newXMLGregorianCalendar(retrieveFlow.reportingFlowDate),
              retrieveFlow.regulation,
              DatatypeFactory.newInstance().newXMLGregorianCalendar(retrieveFlow.regulationDate),
              CtIstitutoMittente(
                CtIdentificativoUnivoco(
                  retrieveFlow.sender._type match {
                    case SenderTypeEnum.ABI_CODE => scalaxbmodel.flussoriversamento.A
                    case SenderTypeEnum.BIC_CODE => scalaxbmodel.flussoriversamento.B
                    case _ => scalaxbmodel.flussoriversamento.GValue
                  },
                  retrieveFlow.sender.id
                ),
                Some(retrieveFlow.sender.pspName)
              ),
              Some(retrieveFlow.bicCodePouringBank),
              CtIstitutoRicevente(
                CtIdentificativoUnivocoPersonaG(
                  scalaxbmodel.flussoriversamento.G,
                  retrieveFlow.receiver.id
                ),
                Some(retrieveFlow.receiver.ecName)
              ),
              retrieveFlow.totPayments,
              retrieveFlow.sumPayments,
            )

            flussoRiversamentoEncoded <- Future.fromTry(XmlEnum.FlussoRiversamento2Str_flussoriversamento(flussoRiversamento))



            retrievePaymentsFlow <- HttpFdrServiceManagement.retrievePaymentsFlow(req.sessionId, req.testCaseId, "retrievePaymentsFlow", Componente.FDR.toString, _fdr,_rev.toString, _psp, actorProps, re.get)
            retrievePaymentsFlow <- HttpFdrServiceManagement.readConfirm(req.sessionId, req.testCaseId, "readConfirm", Componente.FDR.toString, _fdr, _rev.toString, _psp, actorProps, re.get)
          } yield ())
        )
      })
      .recoverWith({ case e: Throwable =>
        log.error(e, s"Errore ")
        for {
          pushRetry <- HttpFdrServiceManagement.pushRetry(req.sessionId, req.testCaseId, "pushRetry", Componente.QUEUE_FDR.toString, _fdr, _psp, actorProps, re.get)
        } yield ()
      })
      .map(_ => {
        log.info(FdrLogConstant.logEnd(actorClassId))
        complete()
      })
  }

  private def parseInput(restRequest: RestRequest) = {
    Try({
//      val psp = restRequest.pathParams("psp")
//      val fdr = restRequest.pathParams("fdr")
//      checkPsp(log, ddataMap, psp) match {
//        case Success(value) => value
//        case Failure(e: DigitPaException) =>
//          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
//        case _ =>
//          throw RestException("", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
//      }
//      CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(fdr, psp) match {
//        case Success(_) => Success(())
//        case Failure(e: DigitPaException) =>
//          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
//        case _ =>
//          throw RestException("", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
//      }

      val nfrReq = if( restRequest.payload.isEmpty ) {
        Failure(RestException("Invalid request", Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue))
      } else {
        JsonValid.check(restRequest.payload.get, JsonEnum.NOTIFY_FLOW) match {
          case Success(_) =>
            val obj = restRequest.payload.get.parseJson.convertTo[NotifyFlowRequest]
            Success(obj)
          case Failure(e) =>
            if (e.getMessage.contains("name")) {
              Failure(RestException("Invalid name", "", StatusCodes.BadRequest.intValue, e))
            } else if (e.getMessage.contains("pspId")) {
              Failure(RestException("Invalid pspId", "", StatusCodes.BadRequest.intValue, e))
            } else if (e.getMessage.contains("retry")) {
              Failure(RestException("Invalid retry", "", StatusCodes.BadRequest.intValue, e))
            } else if (e.getMessage.contains("revision")) {
              Failure(RestException("Invalid revision", "", StatusCodes.BadRequest.intValue, e))
            } else {
              Failure(RestException("Invalid request", "", StatusCodes.BadRequest.intValue, e))
            }
        }
      }

      val psp = nfrReq.get.pspId
      val fdr = nfrReq.get.name
      val revision = nfrReq.get.revision
      val retry = nfrReq.get.retry
      checkPsp(log, ddataMap, psp) match {
        case Success(value) => value
        case Failure(e: DigitPaException) =>
          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
        case _ =>
          throw RestException("", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
      }
      CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(fdr, psp) match {
        case Success(_) => Success(())
        case Failure(e: DigitPaException) =>
          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
        case _ =>
          throw RestException("", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
      }
      _psp = psp
      _fdr = fdr
      _rev = revision
      _retry = retry
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
    RestResponse(req.sessionId, None, httpStatusCode, re, req.testCaseId, exception)
  }

}
