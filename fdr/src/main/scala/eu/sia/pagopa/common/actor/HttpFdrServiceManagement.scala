package eu.sia.pagopa.common.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, RestException}
import eu.sia.pagopa.common.json.model.rendicontazione.{GetPaymentResponse, GetResponse}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.NodoLogger
import spray.json._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object HttpFdrServiceManagement extends HttpBaseServiceManagement {

  def internalGetWithRevision(
                        sessionId: String,
                        testCaseId: Option[String],
                        action: String,
                        receiver: String,
                        fdr: String,
                        rev: String,
                        psp: String,
                        actorProps: ActorProps,
                        re: Re
                      )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {

    val (url, timeout) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`application/json`,
      HttpMethods.GET,
      s"${url.replace("{fdr}",fdr).replace("{revision}",rev).replace("{pspId}",psp)}",
      None,
      Seq(),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    val getResponseData = for {
      httpResponse <- callService(simpleHttpReq, action, receiver, actorProps, false)
      res = {
        if( httpResponse.statusCode != StatusCodes.OK.intValue ) {
          throw new RestException("errore", DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue)
        } else {
          Try(httpResponse.payload.get.parseJson.asInstanceOf[GetResponse]) match {
            case Success(res) => res
            case Failure(e) =>
              throw new RestException(e.getMessage, DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, e)
          }
        }
      }
    } yield res
    getResponseData
  }

  def internalGetFdrPayment(
                    sessionId: String,
                    testCaseId: Option[String],
                    action: String,
                    receiver: String,
                    fdr: String,
                    rev: String,
                    psp: String,
                    actorProps: ActorProps,
                    re: Re
                  )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {

    val (url, timeout) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`application/json`,
      HttpMethods.GET,
      s"${url.replace("{fdr}",fdr).replace("{revision}",rev).replace("{pspId}",psp)}",
      None,
      Seq(),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    val getPaymentResponseData = for {
      httpResponse <- callService(simpleHttpReq, action, receiver, actorProps, false)
      res = {
        if (httpResponse.statusCode != StatusCodes.OK.intValue) {
          throw new RestException("errore", DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue)
        } else {
          Try(httpResponse.payload.get.parseJson.asInstanceOf[GetPaymentResponse]) match {
            case Success(res) => res
            case Failure(e) =>
              throw new RestException(e.getMessage, DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, e)
          }
        }
      }
    } yield res
    getPaymentResponseData
  }

  def pushRetry(
                   sessionId: String,
                   testCaseId: Option[String],
                   action: String,
                   receiver: String,
                   fdr: String,
                   psp: String,
                   actorProps: ActorProps,
                   re: Re
                 )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {

    val (url, timeout) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`application/json`,
      HttpMethods.PUT,
      s"${url.replace("{fdr}", fdr).replace("{psp}", psp)}",
      None,
      Seq(),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps, false)
  }

}
