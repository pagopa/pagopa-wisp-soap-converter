package eu.sia.pagopa.common.actor

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch.MessageDispatcher
import akka.http.impl.engine.client.ProxyConnectionFailedException
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.{ClientTransport, HttpsConnectionContext}
import akka.stream.StreamTcpException
import akka.stream.scaladsl.TcpIdleTimeoutException
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util._

import java.net.InetSocketAddress
import javax.net.ssl.{SSLException, SSLHandshakeException}
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.reflect.ClassTag

class ActorUtility {

  def askBundle[T, S](log: NodoLogger, timeout: FiniteDuration, actor: ActorRef, req: T)(implicit tag: ClassTag[S]): Future[S] = {
    import akka.pattern.ask
    log.debug(s"Call ${actor.path.name} will die in ${timeout.toString()}")
    actor.ask(req)(timeout).mapTo[S]
  }

  def callHttp(req: SimpleHttpReq, actorProps: ActorProps)(implicit log: NodoLogger, system: ActorSystem) = {
    implicit val executionContext: MessageDispatcher = system.dispatchers.lookup("http-dispatcher")

    val akkaheaders = if (req.testCaseId.isDefined) {
      Seq(RawHeader("sid", req.sessionId), RawHeader("tcid", req.testCaseId.get), RawHeader("type", req.messageType))
    } else {
      Nil
    } ++ req.headers.map(t => {
      RawHeader(name = t._1, value = t._2)
    })
    val akkaReq = req.payload match {
      case Some(p) =>
        akka.http.scaladsl.model.HttpRequest(method = req.method, uri = req.uri, entity = HttpEntity.apply(req.contentype, p), headers = akkaheaders)
      case None =>
        akka.http.scaladsl.model.HttpRequest(method = req.method, uri = req.uri, headers = akkaheaders)
    }

    log.info(s"HTTP NODO -> [${req.uri}] will timeout in [${req.timeout.toString}]")
    (for {
      httpResponse <- dispatchRequest(req.timeout, req.proxyData, akkaReq, actorProps.httpsConnectionContext)
      payload <- Unmarshaller.stringUnmarshaller(httpResponse.entity)
      payloadResponse =
        if (payload.nonEmpty) {
          payload.getBytes(Constant.UTF_8)
        } else {
          s"HttpStatus: [${httpResponse.status.value}], headers: [${httpResponse.headers.toString()}]".getBytes(Constant.UTF_8)
        }

      response = SimpleHttpRes(req.sessionId, httpResponse.status.intValue(), httpResponse.headers, Some(payload), None, req.testCaseId)
      _ = log.debug(s"callHttp - end")
    } yield response).recover({ case e: Throwable =>
      log.warn(e, s"callHttp -> [${req.method}] [${req.uri}] -> [${e.getMessage}]")
      val response = SimpleHttpRes(req.sessionId, StatusCodes.InternalServerError.intValue, Seq(), None, Some(handlerReqException(e)), req.testCaseId)
      log.debug(s"callHttp - end")
      response
    })
  }

  private def dispatchRequest(idleTimeout: Duration, proxyData: Option[ProxyData], akkaReq: HttpRequest, ctx:HttpsConnectionContext)(implicit log: NodoLogger, system: ActorSystem): Future[HttpResponse] = {
    val httpsProxyTransport: ClientTransport = {
      if (proxyData.isDefined) {
        val proxyAddress = InetSocketAddress.createUnresolved(proxyData.get.host, proxyData.get.port)
        if (proxyData.get.username.isDefined && proxyData.get.password.isDefined) {
          val proxyAuth = BasicHttpCredentials(proxyData.get.username.get, proxyData.get.password.get)

          log.debug(s"callHttp - dispatchRequest with AUTH PROXY proxyAddress[${proxyAddress.toString}] username[${proxyData.get.username.get}/*******]")
          ClientTransport.httpsProxy(proxyAddress, proxyAuth)
        } else {
          log.debug(s"callHttp - dispatchRequest with PROXY proxyAddress[${proxyAddress.toString}]")
          ClientTransport.httpsProxy(proxyAddress)
        }
      } else {
        log.debug(s"callHttp - dispatchRequest without PROXY")
        ClientTransport.TCP
      }
    }

    val httpConnectTimeout: FiniteDuration = system.settings.config.getInt("config.http.connect-timeout").seconds
    val settings =
      ConnectionPoolSettings(system).withConnectionSettings(ClientConnectionSettings(system).withIdleTimeout(idleTimeout).withConnectingTimeout(httpConnectTimeout).withTransport(httpsProxyTransport))

    if (akkaReq.uri.scheme.toUpperCase == "HTTPS") {
      akka.http.scaladsl.Http().singleRequest(akkaReq, settings = settings, connectionContext = ctx)
    } else {
      akka.http.scaladsl.Http()(system).singleRequest(akkaReq, settings = settings)
    }
  }

  private def handlerReqException(e: Throwable): DigitPaException = {
    e match {
      case e: StreamTcpException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: ProxyConnectionFailedException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: TcpIdleTimeoutException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: SSLHandshakeException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: SSLException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: DigitPaException =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: Throwable if e.getClass.getCanonicalName == "akka.http.impl.engine.client.OutgoingConnectionBlueprint.UnexpectedConnectionClosureException" =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      case e: Throwable =>
        exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
    }
  }

}
