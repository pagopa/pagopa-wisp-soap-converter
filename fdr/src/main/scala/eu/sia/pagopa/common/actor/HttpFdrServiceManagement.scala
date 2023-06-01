package eu.sia.pagopa.common.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, RestException}
import eu.sia.pagopa.common.json.model.rendicontazione.Flow
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import spray.json._

object HttpFdrServiceManagement extends HttpBaseServiceManagement {

  def retrieveFlow(
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
      HttpMethods.GET,
      s"${url.replace("{fdr}",fdr).replace("{psp}",psp)}",
      None,
      Seq(),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    val flowData = for {
      httpResponse <- callService(simpleHttpReq, action, receiver, actorProps, false)
      flow = {
        if( httpResponse.statusCode != StatusCodes.OK.intValue ) {
          throw new RestException("errore", DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue)
        } else {
          Try(httpResponse.payload.get.parseJson.asInstanceOf[Flow]) match {
            case Success(flow) => flow
            case Failure(e) =>
              throw new RestException(e.getMessage, DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, e)
          }
        }
      }
    } yield flow
    flowData
  }

  def retrievePaymentsFlow(
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
      HttpMethods.GET,
      s"${url.replace("{fdr}",fdr).replace("{psp}",psp)}",
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

  def readConfirm(
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
      s"${url.replace("{fdr}",fdr).replace("{psp}",psp)}",
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
