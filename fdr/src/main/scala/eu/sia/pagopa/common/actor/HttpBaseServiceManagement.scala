package eu.sia.pagopa.common.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType => _}
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.Constant.HEADER_SUBSCRIPTION_KEY
import eu.sia.pagopa.common.util.NodoLogger

import scala.concurrent.ExecutionContext
import scala.util.Try

trait HttpBaseServiceManagement {

  def loadServiceConfig(action: String,
                                receiver: String)(implicit log: NodoLogger, as: ActorSystem) = {
    log.debug(s"Load $receiver configuration for $action")

    val url = as.settings.config.getString(s"${receiver.toLowerCase}.$action.url")
    val timeout = as.settings.config.getInt(s"${receiver.toLowerCase}.timeoutSeconds")
    val subKey = Try(Some(as.settings.config.getString(s"${receiver.toLowerCase}.subscriptionKey"))).getOrElse(None)

    val headers = subKey.map(v => Seq(HEADER_SUBSCRIPTION_KEY -> v)).getOrElse(Seq())

    (url, timeout, headers)
  }

  def callService(simpleHttpReq: SimpleHttpReq,
                          action: String,
                          receiver: String,
                          actorProps: ActorProps,
                          isSoapProtocol: Boolean)(implicit log: NodoLogger, ec: ExecutionContext, as: ActorSystem) = {
    log.debug(s"Call $receiver for $action")

    for {
      simpleHttpRes <- actorProps.actorUtility.callHttp(simpleHttpReq, actorProps, isSoapProtocol)
      _ = log.debug(s"Response $receiver for $action")
    } yield simpleHttpRes
  }

}
