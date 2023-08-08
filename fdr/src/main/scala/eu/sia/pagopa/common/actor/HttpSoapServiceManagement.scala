package eu.sia.pagopa.common.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object HttpSoapServiceManagement extends HttpBaseServiceManagement {

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
    val (url, timeout, headers) = loadServiceConfig(action, receiver)

    val simpleHttpReq = SimpleHttpReq(
      sessionId,
      action,
      ContentTypes.`text/xml(UTF-8)`,
      HttpMethods.POST,
      s"$url?${QUERYPARAM_SOAPACTION}=$action",
      Some(payload),
      headers ++ Seq((HEADER_KEY_SOAPACTION, s"\"$action\"")),
      Some(receiver),
      re,
      timeout.seconds,
      None,
      testCaseId
    )

    callService(simpleHttpReq, action, receiver, actorProps, true)
  }

}
