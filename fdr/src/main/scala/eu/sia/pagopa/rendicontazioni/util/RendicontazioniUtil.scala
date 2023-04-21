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
      soapAction: String,
      receiver: String,
      payload: String,
      actorProps: ActorProps
  )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    val (url, timeout) = loadServiceConfig(soapAction, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      soapAction,
      ContentTypes.`text/xml(UTF-8)`,
      HttpMethods.POST,
      s"$url?${QUERYPARAM_SOAPACTION}=$soapAction",
      Some(payload),
      Seq((HEADER_KEY_SOAPACTION, s"\"$soapAction\"")),
      Some(receiver),
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, soapAction, receiver, actorProps)
  }

  def callPrimitiveNew(
                        sessionId: String,
                        testCaseId: Option[String],
                        soapAction: String,
                        receiver: String,
                        payload: String,
                        actorProps: ActorProps
                      )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {

    val (url, timeout) = loadServiceConfig(soapAction, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      soapAction,
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

    callService(simpleHttpReq, soapAction, receiver, actorProps)
  }

  private def loadServiceConfig(soapAction: String,
                                receiver: String)(implicit log: NodoLogger, as: ActorSystem) = {
    log.info(s"Carico configurazione di $receiver per $soapAction")

    val url = as.settings.config.getString(s"${receiver.toLowerCase}.url")
    val timeout = as.settings.config.getInt(s"${receiver.toLowerCase}.timeoutSeconds")

    (url, timeout)
  }


  private def callService(simpleHttpReq: SimpleHttpReq,
                          soapAction: String,
                          receiver: String,
                          actorProps: ActorProps)(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    log.info(s"Chiamo $receiver per $soapAction")

    for {
      simpleHttpRes <- actorProps.actorUtility.callHttp(simpleHttpReq, actorProps)
      _ = log.info(s"Risposta $receiver per $soapAction")
    } yield simpleHttpRes
  }

}
