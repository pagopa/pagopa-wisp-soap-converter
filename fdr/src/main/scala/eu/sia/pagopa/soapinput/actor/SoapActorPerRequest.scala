package eu.sia.pagopa.soapinput.actor

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import eu.sia.pagopa.common.actor.FuturePerRequestActor
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.StringUtils._
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.soapinput.message.SoapRouterRequest
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
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
    reEventFunc: ReEventFunc,
    actorProps: ActorProps
) extends FuturePerRequestActor {

  var message: SoapRouterRequest = _
  var bundleResponse: SoapResponse = _

  override def actorError(dpe: DigitPaException): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, message.sessionId)
    val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
    Util.logPayload(log, Some(payload))
    complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, message.sessionId), Constant.KeyName.SOAP_INPUT)
  }

  private def createHttpResponse(statusCode: StatusCode, payload: String, sessionId: String): HttpResponse = {
    log.debug(s"END request Http [$sessionId]")
    val SESSION_ID_HEADER = true //config.getBoolean("session_id_header")
    HttpResponse(
      status = statusCode,
      entity = HttpEntity(MediaTypes.`text/xml` withCharset HttpCharsets.`UTF-8`, payload.getBytes(Constant.UTF_8)),
      headers = if (SESSION_ID_HEADER) {
        immutable.Seq(RawHeader(Constant.HTTP_RESP_SESSION_ID_HEADER, sessionId))
      } else {
        Nil
      }
    )
  }

  def reExtra(message: SoapRouterRequest): ReExtra =
    ReExtra(uri = message.uri, headers = message.headers.getOrElse(Nil), httpMethod = Some(HttpMethods.POST.toString()), callRemoteAddress = message.callRemoteAddress, soapProtocol = true)

  def traceRequest(message: SoapRouterRequest, reEventFunc: ReEventFunc): Unit = {
    Util.logPayload(log, Some(message.payload))
    val reRequestReq = ReRequest(
      sessionId = message.sessionId,
      testCaseId = message.testCaseId,
      re = Re(
        componente = Componente.FDR.toString,
        categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
        sottoTipoEvento = SottoTipoEvento.REQ.toString,
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(message.sessionId),
        payload = Some(message.payload.getUtf8Bytes),
        insertedTimestamp = message.timestamp,
        erogatore = Some(Componente.FDR.toString),
        erogatoreDescr = Some(Componente.FDR.toString)
      ),
      reExtra = Some(reExtra(message))
    )
    reEventFunc(reRequestReq, log, actorProps.ddataMap)
  }

  override def receive: Receive = {
    case srr: SoapRouterRequest =>
      log.debug("RECEIVE SoapRouterRequest")
      message = srr
      sendToBundle(message)

    case sres: SoapResponse =>
      log.debug("RECEIVE SoapResponse")
      sres.payload match {
        case Some(_) =>
          //risposta dal bundle positiva o negativa
          bundleResponse = sres

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
                  erogatore = Some(Componente.FDR.toString),
                  erogatoreDescr = Some(Componente.FDR.toString)
                )
              ),
            reExtra = Some(ReExtra(statusCode = Some(bundleResponse.statusCode), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS)), soapProtocol = true))
          )

          Util.logPayload(log, sres.payload)
          log.info(FdrLogConstant.callBundle(Constant.KeyName.RE_FEEDER, isInput = false))
          reEventFunc(reRequest, log, actorProps.ddataMap)
          complete(createHttpResponse(StatusCode.int2StatusCode(bundleResponse.statusCode), bundleResponse.payload.getOrElse(""), sres.sessionId), Constant.KeyName.SOAP_INPUT)
        case None =>
          sres.throwable match {
            case Some(e) =>
              //risposta dal dead letter
              log.error(s"Soap Response in errore [${e.getMessage}]")

              traceRequest(message, reEventFunc)

              val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
              val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
              log.info("Genero risposta negativa")
              Util.logPayload(log, sres.payload)

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
                  insertedTimestamp = now
                ),
                reExtra = Some(ReExtra(statusCode = Some(StatusCodes.OK.intValue), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS)), soapProtocol = true))
              )
              reEventFunc(reRequest, log, actorProps.ddataMap)

              complete(createHttpResponse(StatusCodes.OK.intValue, payload, sres.sessionId), Constant.KeyName.SOAP_INPUT)
            case None =>
              //qualche bundle ha risposto in modo sbagliato
              log.error(s"Soap Response in errore")

              traceRequest(message, reEventFunc)

              val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
              val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
              log.info("Genero risposta negativa")
              Util.logPayload(log, sres.payload)

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
                  insertedTimestamp = now
                ),
                reExtra = Some(ReExtra(statusCode = Some(StatusCodes.OK.intValue), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS)), soapProtocol = true))
              )
              reEventFunc(reRequest, log, actorProps.ddataMap)

              complete(createHttpResponse(StatusCodes.OK.intValue, payload, sres.sessionId), Constant.KeyName.SOAP_INPUT)
          }
      }
  }

  def sendToBundle(message: SoapRouterRequest): Try[Unit] = {
    val xmlStreamExc = ("Errore richiesta non leggibile", "soap:Client", "XML_STREAM_EXC")
    val noOperation = ("Operazione non leggibile da SOAPAction o Body", "soap:Client", "NO_OPERATION")
    val soapActionErrata =
      (DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SOAPACTION_ERRATA), DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SOAPACTION_ERRATA), DigitPaErrorCodes.PPT_SOAPACTION_ERRATA)

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
        Failure(SoapRouterException(soapActionErrata._1, e, StatusCodes.BadRequest.intValue, soapActionErrata._3.toString, soapActionErrata._2, Some(s"""SOAPAction "$primitiva" errata""")))
      })
      sender: String = extractSender(xml, xpath._1).getOrElse("n/d")
      routername = Util.getActorRouterName(primitiva, Some(sender))
      router: ActorRef = allRouters.get(routername) match {
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
        message.sessionId,
        message.payload,
        message.callRemoteAddress.getOrElse(""),
        primitiva,
        sender,
        message.timestamp,
        reExtra(message),
        message.testCaseId
      )
    } yield (router, soapRequest)) map { case (router, soapRequest) =>
      log.info(FdrLogConstant.callBundle(router.path.name))
      router ! soapRequest
    } recover {
      case sre: SoapRouterException =>
        log.error(sre, "SoapRouterException")

        traceRequest(message, reEventFunc)

        val payload = Util.faultXmlResponse(sre.faultcode, sre.faultstring, sre.detail)
        Util.logPayload(log, Some(payload))

        val now = Util.now()
        val reRequestResp = ReRequest(
          sessionId = message.sessionId,
          testCaseId = message.testCaseId,
          re = Re(
            componente = Componente.FDR.toString,
            categoriaEvento = CategoriaEvento.INTERFACCIA.toString,
            sottoTipoEvento = SottoTipoEvento.RESP.toString,
            esito = Some(EsitoRE.INVIATA_KO.toString),
            sessionId = Some(message.sessionId),
            payload = Some(payload.getUtf8Bytes),
            insertedTimestamp = now
          ),
          reExtra = Some(ReExtra(statusCode = Some(sre.statusCode), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS)), soapProtocol = true))
        )
        reEventFunc(reRequestResp, log, actorProps.ddataMap)

        complete(createHttpResponse(sre.statusCode, payload, message.sessionId), Constant.KeyName.SOAP_INPUT)

      case e: Throwable =>
        log.error(e, "General Error Throwable")

        traceRequest(message, reEventFunc)

        val dpe = exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
        val payload = Util.faultXmlResponse(dpe.faultCode, dpe.faultString, Some(dpe.message))
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
            insertedTimestamp = now
          ),
          reExtra = Some(ReExtra(statusCode = Some(StatusCodes.InternalServerError.intValue), elapsed = Some(message.timestamp.until(now,ChronoUnit.MILLIS)), soapProtocol = true))
        )
        reEventFunc(reRequest, log, actorProps.ddataMap)
        complete(createHttpResponse(StatusCodes.InternalServerError.intValue, payload, message.sessionId), Constant.KeyName.SOAP_INPUT)
    }
  }

  private def extractSender(xml: NodeSeq, path: String): Try[String] = {
    path.split("/").foldLeft[Try[NodeSeq]](Try(xml))((nodeOption, element) => nodeOption.flatMap(node => Try(node \ element))).flatMap(resultNode => Try(resultNode.head.text))
  }

}
