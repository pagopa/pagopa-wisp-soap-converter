package it.gov.pagopa.soapinput.actor

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import it.gov.pagopa.common.actor.FuturePerRequestActor
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.StringUtils._
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.common.util.azure.cosmos._
import it.gov.pagopa.commonxml.XmlEnum
import it.gov.pagopa.exception.SoapRouterException
import it.gov.pagopa.soapinput.message.SoapRouterRequest
import it.gov.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC

import java.time.temporal.ChronoUnit
import scala.collection.immutable
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

class SoapActorPerRequest(
                           override val requestContext: RequestContext,
                           override val donePromise: Promise[RouteResult],
                           allRouters: Map[String, ActorRef],
                           actorProps: ActorProps
                         ) extends FuturePerRequestActor
  with ReUtil {

  var message: SoapRouterRequest = _
  var bundleResponse: SoapResponse = _

  override def actorError(dpe: DigitPaException): Unit = {
    if (MDC.get(Constant.MDCKey.SESSION_ID) == null) {
      MDC.put(Constant.MDCKey.SESSION_ID, message.sessionId)
    }
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
    Util.logPayload(log, Some(payload))
    complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, MDC.get(Constant.MDCKey.SESSION_ID)), Constant.KeyName.SOAP_INPUT)
    //MDC.remove(Constant.MDCKey.SESSION_ID)
  }

  private def createHttpResponse(statusCode: StatusCode, payload: String, sessionId: String): HttpResponse = {
    log.debug(s"END request Http [$sessionId]")
    HttpResponse(
      status = statusCode,
      entity = HttpEntity(MediaTypes.`text/xml` withCharset HttpCharsets.`UTF-8`, payload.getBytes(Constant.UTF_8)),
      headers = immutable.Seq(RawHeader(Constant.HTTP_RESP_SESSION_ID_HEADER, sessionId))
    )
  }

  override def receive: Receive = {
    case srr: SoapRouterRequest =>
      log.debug("RECEIVE SoapRouterRequest")
      message = srr
      sendToBundle(message)

    case sres: SoapResponse =>
      log.debug("RECEIVE SoapResponse")
      //risposta dal bundle positiva o negativa
      bundleResponse = sres

      val now = Util.now()
      val sessionId = MDC.get(Constant.MDCKey.SESSION_ID);
      val reRequest = ReRequest(
        sessionId = sessionId,
        testCaseId = sres.testCaseId,
        re = sres.re
          .map(
            _.copy(
              componente = Componente.WISP_SOAP_CONVERTER,
              categoriaEvento = CategoriaEvento.INTERFACE,
              sottoTipoEvento = SottoTipoEvento.RESP,
              esito = Esito.SEND,
              payload = Some(sres.payload.getUtf8Bytes),
              insertedTimestamp = now
            )
          )
          .getOrElse(
            Re(
              componente = Componente.WISP_SOAP_CONVERTER,
              categoriaEvento = CategoriaEvento.INTERFACE,
              sottoTipoEvento = SottoTipoEvento.RESP,
              esito = Esito.SEND,
              payload = Some(sres.payload.getUtf8Bytes),
              insertedTimestamp = now,
              sessionId = Some(sessionId),
              erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
              erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
            )
          ),
        reExtra = Some(ReExtra(callType = Some(CallType.SERVER), statusCode = Some(bundleResponse.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS)), soapProtocol = true))
      )

      Util.logPayload(log, Some(sres.payload))
      log.info(LogConstant.callBundle(Constant.KeyName.RE_FEEDER, isInput = false))
      actorProps.reEventFunc(reRequest, log, actorProps.ddataMap)
      complete(createHttpResponse(StatusCode.int2StatusCode(bundleResponse.statusCode), bundleResponse.payload, MDC.get(Constant.MDCKey.SESSION_ID)), Constant.KeyName.SOAP_INPUT)
  }

  def sendToBundle(message: SoapRouterRequest): Try[Unit] = {
    val xmlStreamExc = ("Errore richiesta non leggibile", "soap:Client", "XML_STREAM_EXC")
    val noOperation = ("Operazione non leggibile da SOAPAction o Body", "soap:Client", "NO_OPERATION")
    val soapActionErrata =
      (DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SOAPACTION_ERRATA), DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SOAPACTION_ERRATA), DigitPaErrorCodes.PPT_SOAPACTION_ERRATA)
    val sessionId = MDC.get(Constant.MDCKey.SESSION_ID);

    (for {
      xml <- Try(XmlEnum.parseXmlString(message.payload)).recover({ case e: Throwable =>
        log.warn(e, s"${xmlStreamExc._1} - ${e.getMessage}")
        throw SoapRouterException(xmlStreamExc._1, e, StatusCodes.BadRequest.intValue, xmlStreamExc._2, xmlStreamExc._3, Some(xmlStreamExc._1))
      })
      primitiva <- {
        val soapAction = message.soapAction.map(_.replaceAll("\"", ""))
        if (soapAction.isEmpty) {
          for {
            soapAction <- Try((xml \ "Body" \ "_").head.label).recover({ case e: Throwable =>
              log.warn(e, s"${noOperation._1} - ${e.getMessage}")
              throw SoapRouterException(noOperation._1, e, StatusCodes.BadRequest.intValue, noOperation._2, noOperation._3, Some(noOperation._1))
            })
          } yield soapAction
        } else {
          Success(soapAction.get)
        }
      }

      _ = MDC.put(Constant.MDCKey.ACTOR_CLASS_ID, primitiva)
      xpath <- Try(Primitive.soap(primitiva)).recoverWith({ case e =>
        Failure(SoapRouterException(soapActionErrata._1, e, StatusCodes.BadRequest.intValue, soapActionErrata._3.toString, soapActionErrata._2, Some(s"""SOAPAction "${primitiva}" errata""")))
      })
      sender: String = extractSender(xml, xpath._1).getOrElse("n/d")
      routername = Util.getActorRouterName(primitiva, Some(sender))
      router: ActorRef =
        allRouters.get(routername) match {
          case Some(routerObj) =>
            log.debug(s"ROUTER for [$primitiva - $sender] FOUND [${routerObj.path}]")
            routerObj

          case None =>
            allRouters.get(BootstrapUtil.actorRouter(primitiva)) match {
              case Some(defaultRouter) =>
                log.debug(s"ROUTER for [$primitiva - DEFAULT] FOUND [${defaultRouter.path}]")
                defaultRouter
              case None =>
                log.error(s"ROUTER for [$primitiva] not found")
                val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
                throw dpe
            }
        }
      soapRequest = SoapRequest(
        sessionId,
        message.payload,
        message.callRemoteAddress.getOrElse(""),
        primitiva,
        sender,
        message.timestamp,
        reExtra(message), false,
        message.testCaseId
      )
    } yield (router, soapRequest)) map { case (router, soapRequest) =>
      router ! soapRequest
    } recover {
      case sre: SoapRouterException =>
        log.error(sre, "SoapRouterException")

        traceRequest(message, actorProps.reEventFunc, actorProps.ddataMap)

        val payload = Util.faultXmlResponse(sre.faultcode, sre.faultstring, sre.detail)
        Util.logPayload(log, Some(payload))

        val now = Util.now()
        val reRequestResp = ReRequest(
          sessionId = sessionId,
          testCaseId = message.testCaseId,
          re = Re(
            componente = Componente.WISP_SOAP_CONVERTER,
            categoriaEvento = CategoriaEvento.INTERFACE,
            sottoTipoEvento = SottoTipoEvento.RESP,
            esito = Esito.SEND_FAILURE,
            sessionId = Some(sessionId),
            payload = Some(payload.getUtf8Bytes),
            insertedTimestamp = now
          ),
          reExtra = Some(ReExtra(callType = Some(CallType.SERVER), statusCode = Some(sre.statusCode), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS)), soapProtocol = true))
        )
        actorProps.reEventFunc(reRequestResp, log, actorProps.ddataMap)

        complete(createHttpResponse(sre.statusCode, payload, sessionId), Constant.KeyName.SOAP_INPUT)

      case e: Throwable =>
        log.error(e, "General Error Throwable")

        traceRequest(message, actorProps.reEventFunc, actorProps.ddataMap)

        val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
        val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
        Util.logPayload(log, Some(payload))

        val now = Util.now()
        val reRequest = ReRequest(
          sessionId = sessionId,
          testCaseId = message.testCaseId,
          re = Re(
            componente = Componente.WISP_SOAP_CONVERTER,
            categoriaEvento = CategoriaEvento.INTERFACE,
            sottoTipoEvento = SottoTipoEvento.RESP,
            esito = Esito.SEND_FAILURE,
            sessionId = Some(sessionId),
            payload = Some(payload.getUtf8Bytes),
            insertedTimestamp = now
          ),
          reExtra = Some(ReExtra(callType = Some(CallType.SERVER), statusCode = Some(StatusCodes.InternalServerError.intValue), elapsed = Some(message.timestamp.until(now, ChronoUnit.MILLIS)), soapProtocol = true))
        )
        actorProps.reEventFunc(reRequest, log, actorProps.ddataMap)
        complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, sessionId), Constant.KeyName.SOAP_INPUT)
    }
  }

  def traceRequest(message: SoapRouterRequest, reEventFunc: ReEventFunc, ddataMap: ConfigData): Unit = {
    Util.logPayload(log, Some(message.payload))
    val sessionId = MDC.get(Constant.MDCKey.SESSION_ID);
    val reRequestReq = ReRequest(
      sessionId = sessionId,
      testCaseId = message.testCaseId,
      re = Re(
        componente = Componente.WISP_SOAP_CONVERTER,
        categoriaEvento = CategoriaEvento.INTERFACE,
        sottoTipoEvento = SottoTipoEvento.REQ,
        esito = Esito.RECEIVED,
        sessionId = Some(sessionId),
        payload = Some(message.payload.getUtf8Bytes),
        insertedTimestamp = message.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
      ),
      reExtra = Some(reExtra(message))
    )
    reEventFunc(reRequestReq, log, ddataMap)
  }

  def reExtra(message: SoapRouterRequest): ReExtra =
    ReExtra(callType = Some(CallType.SERVER), uri = message.uri, headers = message.headers.getOrElse(Nil), httpMethod = Some(HttpMethods.POST.toString()), callRemoteAddress = message.callRemoteAddress, soapProtocol = true)

  private def extractSender(xml: NodeSeq, path: String): Try[String] = {
    path.split("/").foldLeft[Try[NodeSeq]](Try(xml))((nodeOption, element) => nodeOption.flatMap(node => Try(node \ element))).flatMap(resultNode => Try(resultNode.head.text))
  }

}
