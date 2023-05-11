package eu.sia.pagopa.common.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object HttpServiceManagement {

  private val HEADER_KEY_SOAPACTION = "SOAPAction"

  private val QUERYPARAM_SOAPACTION = "soapAction"

  def createRequestSoapAction(
      sessionId: String,
      testCaseId: Option[String],
      action: String,
      receiver: String,
      payload: String,
      actorProps: ActorProps,
      re: Re
  )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    val (url, timeout) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`text/xml(UTF-8)`,
      HttpMethods.POST,
      s"$url?${QUERYPARAM_SOAPACTION}=$action",
      Some(payload),
      Seq((HEADER_KEY_SOAPACTION, s"\"$action\"")),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps, true)
  }

  def createRequestRestAction(
                        sessionId: String,
                        testCaseId: Option[String],
                        action: String,
                        receiver: String,
                        payload: String,
                        actorProps: ActorProps,
                        re: Re
                      )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {

    val (url, timeout) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`application/json`,
      HttpMethods.POST,
      s"$url",
      Some(payload),
      Seq(),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps, false)
  }

  private def loadServiceConfig(action: String,
                                receiver: String)(implicit log: NodoLogger, as: ActorSystem) = {
    log.debug(s"Load $receiver configuration for $action")

    val url = as.settings.config.getString(s"${receiver.toLowerCase}.url")
    val timeout = as.settings.config.getInt(s"${receiver.toLowerCase}.timeoutSeconds")

    (url, timeout)
  }

  private def callService(simpleHttpReq: SimpleHttpReq,
                          action: String,
                          receiver: String,
                          actorProps: ActorProps,
                          isSoapProtocol: Boolean)(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    log.info(s"Call $receiver for $action")

    for {
      simpleHttpRes <- actorProps.actorUtility.callHttp(simpleHttpReq, actorProps, isSoapProtocol)
      _ = log.info(s"Response $receiver for $action")
    } yield simpleHttpRes
  }

}
