package eu.sia.pagopa.rendicontazioni.actor.rest

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.{HttpFdrServiceManagement, PerRequestActor}
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException, RestException}
import eu.sia.pagopa.common.json.model.rendicontazione._
import eu.sia.pagopa.common.json.{JsonEnum, JsonValid}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.DDataChecks.checkPsp
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XmlUtil
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.BaseFlussiRendicontazioneActor
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import org.slf4j.MDC
import scalaxbmodel.flussoriversamento.{CtDatiSingoliPagamenti, CtFlussoRiversamento, CtIdentificativoUnivoco, CtIdentificativoUnivocoPersonaG, CtIstitutoMittente, CtIstitutoRicevente, Number1u461}
import scalaxbmodel.nodoperpsp.NodoInviaFlussoRendicontazione
import spray.json._

import javax.xml.datatype.DatatypeFactory
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

final case class NotifyFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
  extends PerRequestActor with BaseFlussiRendicontazioneActor with ReUtil {

  var req: RestRequest = _
  var replyTo: ActorRef = _

  private var _psp: String = _
  private var _fdr: String = _
  private var _rev: Integer = _
  private var _retry: Integer = _

  var reFlow: Option[Re] = None

  override def receive: Receive = {
    case restRequest: RestRequest =>
      replyTo = sender()
      req = restRequest

      reFlow = Some(
        Re(
          componente = Componente.NDP_FDR.toString,
          categoriaEvento = CategoriaEvento.INTERNO.toString,
          sessionId = Some(req.sessionId),
          payload = None,
          esito = Some(EsitoRE.CAMBIO_STATO.toString),
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN.toString,
          insertedTimestamp = restRequest.timestamp,
          erogatore = Some(Componente.NDP_FDR.toString),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(Componente.NDP_FDR.toString),
          flowAction = Some(req.primitive)
        )
      )

      (for {
        _ <- Future.successful(())
        _ = log.info(FdrLogConstant.logSintattico(actorClassId))
        _ <- Future.fromTry(parseInput(req))

        re_ = Re(
          psp = Some(_psp),
          componente = Componente.NDP_FDR.toString,
          categoriaEvento = CategoriaEvento.INTERNO.toString,
          sessionId = Some(req.sessionId),
          payload = None,
          esito = Some(EsitoRE.CAMBIO_STATO.toString),
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN.toString,
          insertedTimestamp = restRequest.timestamp,
          erogatore = Some(Componente.NDP_FDR.toString),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(Componente.NDP_FDR.toString),
          flowName = Some(_fdr),
          flowAction = Some(req.primitive)
        )
        _ = reFlow = Some(re_)

        getResponse <- HttpFdrServiceManagement.internalGetWithRevision(req.sessionId, req.testCaseId, "internalGetWithRevision", Componente.FDR.toString, _fdr, _rev.toString, _psp, actorProps, reFlow.get)

        getPaymentResponse <- HttpFdrServiceManagement.internalGetFdrPayment(req.sessionId, req.testCaseId, "internalGetFdrPayment", Componente.FDR.toString, _fdr, _rev.toString, _psp, actorProps, reFlow.get)

        _ = log.info(FdrLogConstant.logGeneraPayload(s"nodoInviaFlussoRendicontazione SOAP"))
        flussoRiversamento = CtFlussoRiversamento(
          Number1u461,
          getResponse.fdr,
          DatatypeFactory.newInstance().newXMLGregorianCalendar(getResponse.fdrDate),
          getResponse.regulation,
          DatatypeFactory.newInstance().newXMLGregorianCalendar(getResponse.regulationDate),
          CtIstitutoMittente(
            CtIdentificativoUnivoco(
              getResponse.sender._type match {
                case SenderTypeEnum.ABI_CODE => scalaxbmodel.flussoriversamento.A
                case SenderTypeEnum.BIC_CODE => scalaxbmodel.flussoriversamento.B
                case _ => scalaxbmodel.flussoriversamento.GValue
              },
              getResponse.sender.id
            ),
            Some(getResponse.sender.pspName)
          ),
          Some(getResponse.bicCodePouringBank),
          CtIstitutoRicevente(
            CtIdentificativoUnivocoPersonaG(
              scalaxbmodel.flussoriversamento.G,
              getResponse.receiver.id
            ),
            Some(getResponse.receiver.organizationName)
          ),
          getResponse.computedTotPayments,
          getResponse.computedSumPayments,
          getPaymentResponse.data.map(p => {
            CtDatiSingoliPagamenti(
              p.iuv,
              p.iur,
              Some(BigInt.int2bigInt(p.index)),
              p.pay,
              p.payStatus match {
                case PayStatusEnum.NO_RPT => scalaxbmodel.flussoriversamento.Number9
                case PayStatusEnum.REVOKED => scalaxbmodel.flussoriversamento.Number3
                case _ => scalaxbmodel.flussoriversamento.Number0
              },
              DatatypeFactory.newInstance().newXMLGregorianCalendar(p.payDate)
            )
          })
        )
        flussoRiversamentoEncoded <- Future.fromTry(XmlEnum.FlussoRiversamento2Str_flussoriversamento(flussoRiversamento))
        flussoRiversamentoBase64 = XmlUtil.StringBase64Binary.encodeBase64(flussoRiversamentoEncoded)

        nifr = NodoInviaFlussoRendicontazione(
          getResponse.sender.pspId,
          getResponse.sender.pspBrokerId,
          getResponse.sender.channelId,
          getResponse.sender.password,
          getResponse.receiver.organizationId,
          getResponse.fdr,
          DatatypeFactory.newInstance().newXMLGregorianCalendar(getResponse.fdrDate),
          flussoRiversamentoBase64
        )

        (pa, _, _) <- Future.fromTry(checks(ddataMap, nifr, false, actorClassId))

        (esito, _, sftpFile, _) <- saveRendicontazione(
          getResponse.fdr,
          getResponse.sender.pspId,
          getResponse.sender.pspBrokerId,
          getResponse.sender.channelId,
          getResponse.receiver.organizationId,
          DatatypeFactory.newInstance().newXMLGregorianCalendar(getResponse.fdrDate),
          flussoRiversamentoBase64,
          flussoRiversamentoEncoded,
          flussoRiversamento,
          pa,
          ddataMap,
          actorClassId,
          repositories.fdrRepository
        )

//        _ <-
//          if (sftpFile.isDefined) {
//            notifySFTPSender(pa, req.sessionId, req.testCaseId, sftpFile.get).flatMap(resp => {
//              if (resp.throwable.isDefined) {
//                //HOTFIX non torno errore al chiamante se ftp non funziona
//                log.warn(s"Error sending file first time for reporting flow [${resp.throwable.get.getMessage}]")
//                Future.successful(())
//              } else {
//                Future.successful(())
//              }
//            })
//          } else {
//            Future.successful(())
//          }
//
        _ = if (esito == Constant.KO) {
          throw RestException("Error saving fdr on Db", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
        } else {
          Future.successful(())
        }
      } yield RestResponse(req.sessionId, Some(GenericResponse(GenericResponseOutcome.OK, None).toJson.toString), StatusCodes.OK.intValue, reFlow, req.testCaseId, None) )
        .recoverWith({
          case rex: RestException =>
            Future.successful(generateResponse(Some(rex)))
          case cause: Throwable =>
            val pmae = RestException(DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, cause)
            Future.successful(generateResponse(Some(pmae)))
      }).map( res => {
        traceInterfaceRequest(req, reFlow.get, req.reExtra, reEventFunc, ddataMap)
        log.info(FdrLogConstant.logEnd(actorClassId))
        replyTo ! res
        complete()
      })
  }

  private def parseInput(restRequest: RestRequest) = {
    Try({
      val nfrReq = if( restRequest.payload.isEmpty ) {
        Failure(RestException("Invalid request", Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue))
      } else {
        JsonValid.check(restRequest.payload.get, JsonEnum.NOTIFY_FLOW) match {
          case Success(_) =>
            val obj = restRequest.payload.get.parseJson.convertTo[NotifyFdrRequest]
            Success(obj)
          case Failure(e) =>
            if (e.getMessage.contains("fdr")) {
              Failure(RestException("Invalid fdr", "", StatusCodes.BadRequest.intValue, e))
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
      val fdr = nfrReq.get.fdr
      val revision = nfrReq.get.revision
      val retry = nfrReq.get.retry
      checkPsp(log, ddataMap, psp) match {
        case Success(value) => value
        case Failure(e: DigitPaException) =>
          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
        case _ =>
          throw RestException("Error during check psp", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
      }
      CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(fdr, psp) match {
        case Success(_) => Success(())
        case Failure(e: DigitPaException) =>
          throw RestException(e.getMessage, Constant.HttpStatusDescription.BAD_REQUEST, StatusCodes.BadRequest.intValue)
        case _ =>
          throw RestException("Error during check fdr format", Constant.HttpStatusDescription.INTERNAL_SERVER_ERROR, StatusCodes.InternalServerError.intValue)
      }
      _psp = psp
      _fdr = fdr
      _rev = revision
      _retry = retry
    })
  }

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, dpe, reFlow)
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
    val err = GenericResponse(GenericResponseOutcome.KO, Some(restException.message)).toJson.toString()
    RestResponse(sessionId, Some(err), restException.statusCode, re, tcid, Some(restException))
  }

  private def generateResponse(exception: Option[RestException]) = {
    log.info(FdrLogConstant.logGeneraPayload(actorClassId + "Risposta"))
    val httpStatusCode = exception.map(_.statusCode).getOrElse(StatusCodes.OK.intValue)
    log.debug(s"Generazione risposta $httpStatusCode")
    val responsePayload = exception.map(v => GenericResponse(GenericResponseOutcome.KO, Some(v.jsonMessage)).toJson.toString())
    RestResponse(req.sessionId, responsePayload, httpStatusCode, reFlow, req.testCaseId, exception)
  }

}
