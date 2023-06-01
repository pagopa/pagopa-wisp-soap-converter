package eu.sia.pagopa.restinput.actor

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.FuturePerRequestActor
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException, RestException}
import eu.sia.pagopa.common.json.model.Error
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.StringUtils._
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc
import eu.sia.pagopa.restinput.message.RestRouterRequest
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import spray.json._

import java.time.temporal.ChronoUnit
import scala.concurrent.Promise

class RestActorPerRequest(
    override val requestContext: RequestContext,
    override val donePromise: Promise[RouteResult],
    allRouters: Map[String, ActorRef],
    reEventFunc: ReEventFunc,
    actorProps: ActorProps
) extends FuturePerRequestActor {

  var message: RestRouterRequest = _
  var bundleResponse: RestResponse = _

  var params: Map[String, String] = _
  var httpMethod: Option[String] = _

  override def actorError(dpe: DigitPaException): Unit = {
    val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
    val payload = Error(dpe.message).toJson.toString()
    Util.logPayload(log, Some(payload))
    complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, message.sessionId), Constant.KeyName.REST_INPUT)
  }

  private def createHttpResponse(statusCode: StatusCode, payload: String, sessionId: String): HttpResponse = {
    log.debug(s"END request Http [$sessionId]")
    val SESSION_ID_HEADER = true
    HttpResponse(
      status = statusCode,
      entity = HttpEntity(MediaTypes.`application/json`, payload.getBytes(Constant.UTF_8)),
      headers = if (SESSION_ID_HEADER) {
        RawHeader(Constant.HTTP_RESP_SESSION_ID_HEADER, sessionId) :: Nil
      } else {
        Nil
      }
    )
  }

  def reExtra(rrr: RestRouterRequest): ReExtra =
    ReExtra(uri = rrr.uri.map(_.toString), headers = rrr.headers, httpMethod = rrr.httpMethod, callRemoteAddress = rrr.callRemoteAddress)

  def traceRequest(rrr: RestRouterRequest, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    Util.logPayload(log, message.payload)
    val reRequestReq = ReRequest(
      sessionId = rrr.sessionId,
      testCaseId = rrr.testCaseId,
      re = Re(
        componente = Componente.FDR.toString,
        categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
        sottoTipoEvento = SottoTipoEvento.REQ.toString,
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(rrr.sessionId),
        payload = rrr.payload.map(_.getUtf8Bytes),
        insertedTimestamp = rrr.timestamp,
        info = Some(rrr.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", "))
      ),
      reExtra = Some(reExtra(rrr))
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

  override def receive: Receive = {
    case srr: RestRouterRequest =>
      log.debug("RECEIVE RestRouterRequest")
      message = srr
      log.info(FdrLogConstant.callBundle(Constant.KeyName.RE_FEEDER, isInput = true))
      sendToBundle(message)

    case sres: RestResponse =>
      sres.payload match {
        case Some(_) =>
          //risposta dal bundle positiva o negativa
          bundleResponse = sres

          log.debug("RECEIVE RestResponse")
          log.info(FdrLogConstant.callBundle(Constant.KeyName.RE_FEEDER, isInput = false))

          val now = Util.now()
          val reRequest = ReRequest(
            sessionId = sres.sessionId,
            testCaseId = sres.testCaseId,
            re = sres.re
              .map(
                _.copy(
                  componente = Componente.FDR.toString,
                  categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                  sottoTipoEvento = SottoTipoEvento.RESP.toString,
                  esito = Some(EsitoRE.INVIATA.toString),
                  payload = sres.payload.map(_.getUtf8Bytes),
                  insertedTimestamp = now
                )
              )
              .getOrElse(
                Re(
                  componente = Componente.FDR.toString,
                  categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                  sottoTipoEvento = SottoTipoEvento.RESP.toString,
                  esito = Some(EsitoRE.INVIATA.toString),
                  payload = sres.payload.map(_.getUtf8Bytes),
                  insertedTimestamp = now,
                  sessionId = Some(sres.sessionId),
                  info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", "))
                )
              ),
            reExtra = Some(ReExtra(statusCode = Some(bundleResponse.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS))))
          )
          Util.logPayload(log, sres.payload)
          reEventFunc(reRequest, log, actorProps.ddataMap)
          complete(createHttpResponse(StatusCode.int2StatusCode(sres.statusCode), sres.payload.getOrElse(""), sres.sessionId), Constant.KeyName.REST_INPUT)
        case None =>
          sres.throwable match {
            case Some(e: RestException) =>
              log.error(e, s"Rest Response in errore [${e.getMessage}]")

              log.info("Genero risposta negativa")
              val payload = Error(e.message).toJson.toString()
              Util.logPayload(log, Some(payload))

              val now = Util.now()
              val reRequest = ReRequest(
                sessionId = message.sessionId,
                testCaseId = message.testCaseId,
                re = Re(
                  componente = Componente.FDR.toString,
                  categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                  sottoTipoEvento = SottoTipoEvento.RESP.toString,
                  esito = Some(EsitoRE.INVIATA_KO.toString),
                  sessionId = Some(message.sessionId),
                  payload = Some(payload.getUtf8Bytes),
                  insertedTimestamp = now,
                  info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", "))
                ),
                reExtra = Some(ReExtra(statusCode = Some(e.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS))))
              )
              reEventFunc(reRequest, log, actorProps.ddataMap)
//              traceRequest(message, reEventFunc, actorProps.ddataMap)
              complete(createHttpResponse(e.statusCode, payload, sres.sessionId), Constant.KeyName.REST_INPUT)
            case Some(e: Throwable) =>
              log.error(e, s"Rest Response in errore [${e.getMessage}]")

              val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
              log.info("Genero risposta negativa")
              val payload = Error(dpe.message).toJson.toString()
              Util.logPayload(log, Some(payload))

              val now = Util.now()
              val reRequest = ReRequest(
                sessionId = message.sessionId,
                testCaseId = message.testCaseId,
                re = Re(
                  componente = Componente.FDR.toString,
                  categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                  sottoTipoEvento = SottoTipoEvento.RESP.toString,
                  esito = Some(EsitoRE.INVIATA_KO.toString),
                  sessionId = Some(message.sessionId),
                  payload = Some(payload.getUtf8Bytes),
                  insertedTimestamp = now,
                  info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", "))
                ),
                reExtra = Some(ReExtra(statusCode = Some(StatusCodes.InternalServerError.intValue), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS))))
              )
              reEventFunc(reRequest, log, actorProps.ddataMap)
              traceRequest(message, reEventFunc, actorProps.ddataMap)
              complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, sres.sessionId), Constant.KeyName.REST_INPUT)
            case None =>
              val now = Util.now()
              val (reRequest, payload) = sres.statusCode match {
                case StatusCodes.OK.intValue =>
                  (ReRequest(
                    sessionId = message.sessionId,
                    testCaseId = message.testCaseId,
                    re = Re(
                      componente = Componente.FDR.toString,
                      categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                      sottoTipoEvento = SottoTipoEvento.RESP.toString,
                      esito = Some(EsitoRE.INVIATA.toString),
                      sessionId = Some(message.sessionId),
                      payload = sres.payload.map(_.getUtf8Bytes),
                      insertedTimestamp = now,
                      info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", ")),
                      fruitore = Some(Componente.FDR_NOTIFIER.toString)
                    ),
                    reExtra = Some(ReExtra(statusCode = Some(sres.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS))))
                  ), sres.payload)
                case _ =>
                  val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
                  log.info("Genero risposta negativa")
                  val errPayload = Error(dpe.message).toJson.toString()
                  Util.logPayload(log, sres.payload)
                  (ReRequest(
                    sessionId = message.sessionId,
                    testCaseId = message.testCaseId,
                    re = Re(
                      componente = Componente.FDR.toString,
                      categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
                      sottoTipoEvento = SottoTipoEvento.RESP.toString,
                      esito = Some(EsitoRE.INVIATA_KO.toString),
                      sessionId = Some(message.sessionId),
                      payload = Some(errPayload.getUtf8Bytes),
                      insertedTimestamp = now,
                      info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", ")),
                      fruitore = Some(Componente.FDR_NOTIFIER.toString)
                    ),
                    reExtra = Some(ReExtra(statusCode = Some(sres.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS))))
                  ), Some(errPayload))
              }
              reEventFunc(reRequest, log, actorProps.ddataMap)
              traceRequest(message, reEventFunc, actorProps.ddataMap)
              complete(createHttpResponse(sres.statusCode, payload.map(v => v).getOrElse(""), sres.sessionId), Constant.KeyName.REST_INPUT)
          }
      }
  }

  def sendToBundle(message: RestRouterRequest): Unit = {
    allRouters.get(BootstrapUtil.actorRouter(message.primitiva)) match {
      case Some(router) =>
        val restRequest =
          RestRequest(message.sessionId, message.payload, message.queryParams, message.pathParams, message.callRemoteAddress.getOrElse(""), message.primitiva, message.timestamp, reExtra(message), message.testCaseId)
        log.info(FdrLogConstant.callBundle(router.path.name))
        router ! restRequest

      case None =>
        log.error(s"Router [${message.primitiva}] not found")

        traceRequest(message, reEventFunc, actorProps.ddataMap)

        val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
        val payload = Error(dpe.message).toJson.toString()
        Util.logPayload(log, message.payload)

        val now = Util.now()
        val reRequest = ReRequest(
          sessionId = message.sessionId,
          testCaseId = message.testCaseId,
          re = Re(
            componente = Componente.FDR.toString,
            categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
            sottoTipoEvento = SottoTipoEvento.RESP.toString,
            esito = Some(EsitoRE.INVIATA_KO.toString),
            sessionId = Some(message.sessionId),
            payload = Some(payload.getUtf8Bytes),
            insertedTimestamp = now,
            info = Some(message.queryParams.map(a => s"${a._1}=[${a._2}]").mkString(", "))
          ),
          reExtra = Some(ReExtra(statusCode = Some(StatusCodes.InternalServerError.intValue), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS))))
        )
        reEventFunc(reRequest, log, actorProps.ddataMap)
        complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, message.sessionId), Constant.KeyName.REST_INPUT)
    }
  }

}
