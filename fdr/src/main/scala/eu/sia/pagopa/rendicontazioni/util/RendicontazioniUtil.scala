package eu.sia.pagopa.rendicontazioni.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object RendicontazioniUtil {

  def callPrimitive(
      sessionId: String,
      testCaseId: Option[String],
      soapAction: String,
      receiver: String,
      payload: String,
      actorProps: ActorProps
  )(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    log.info(s"calling $soapAction $receiver")

    val url = as.settings.config.getString(s"${receiver.toLowerCase}.url")
    val timeout = as.settings.config.getInt(s"${receiver.toLowerCase}.timeoutSeconds")

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      soapAction,
      ContentTypes.`text/xml(UTF-8)`,
      HttpMethods.POST,
      s"$url?soapAction=$soapAction",
      Some(payload),
      Seq(("SOAPAction", s"\"$soapAction\"")),
      Some(receiver),
      timeout.seconds,
      None,
      testCaseId
    )

    actorProps.actorUtility.callHttp(simpleHttpReq, actorProps)

  }

}
