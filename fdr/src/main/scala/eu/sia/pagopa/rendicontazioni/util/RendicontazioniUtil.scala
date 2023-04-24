package eu.sia.pagopa.rendicontazioni.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object RendicontazioniUtil {

  private val HEADER_KEY_SOAPACTION = "SOAPAction"

  private val QUERYPARAM_SOAPACTION = "soapAction"

  def callPrimitiveOld(
      sessionId: String,
      testCaseId: Option[String],
      action: String,
      receiver: String,
      payload: String,
      actorProps: ActorProps
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
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps)
  }

  def callPrimitiveNew(
                        sessionId: String,
                        testCaseId: Option[String],
                        action: String,
                        receiver: String,
                        payload: String,
                        actorProps: ActorProps
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
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps)
  }

  private def loadServiceConfig(action: String,
                                receiver: String)(implicit log: NodoLogger, as: ActorSystem) = {
    log.info(s"Carico configurazione di $receiver per $action")

    val url = as.settings.config.getString(s"${receiver.toLowerCase}.url")
    val timeout = as.settings.config.getInt(s"${receiver.toLowerCase}.timeoutSeconds")

    (url, timeout)
  }

  private def callService(simpleHttpReq: SimpleHttpReq,
                          action: String,
                          receiver: String,
                          actorProps: ActorProps)(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    log.info(s"Chiamo $receiver per $action")

    for {
      simpleHttpRes <- actorProps.actorUtility.callHttp(simpleHttpReq, actorProps)
      _ = log.info(s"Risposta $receiver per $action")
    } yield simpleHttpRes
  }

}
