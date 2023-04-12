package eu.sia.pagopa.common.actor

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ Actor, OneForOneStrategy, ReceiveTimeout, SupervisorStrategy }
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.RequestContext
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{ DigitPaErrorCodes, DigitPaException }
import eu.sia.pagopa.common.util.NodoLogConstant

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContextExecutor, Future, Promise }

trait FuturePerRequestActor extends Actor with NodoLogging {
  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  implicit val actorPathName: String = self.path.name

  val requestContext: RequestContext
  val donePromise: Promise[akka.http.scaladsl.server.RouteResult]

  val BUNDLE_IDLE_TIMEOUT =
    FiniteDuration(context.system.settings.config.getInt("bundleTimeoutSeconds"), TimeUnit.SECONDS)
  context.setReceiveTimeout(BUNDLE_IDLE_TIMEOUT)

  val actorName: String = self.path.name
  log.debug(s"FuturePerRequest - This futureActor [$actorName] will die in [${BUNDLE_IDLE_TIMEOUT.toString}]")

  override val supervisorStrategy: SupervisorStrategy = {
    OneForOneStrategy() { case e: Throwable =>
      val dpe = DigitPaException("FuturePerRequest - FutureActor SupervisorStrategy Error", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      log.error(dpe, dpe.getMessage)
      actorError(dpe)
      Stop
    }
  }

  context.become(receive orElse baseReceive)
  def baseReceive: Receive = {
    case ReceiveTimeout =>
      val dpe =
        exception.DigitPaException("FuturePerRequest - FutureActor Internal Timeout Error", DigitPaErrorCodes.PPT_SYSTEM_ERROR)
      log.error(dpe, dpe.getMessage)
      actorError(dpe)
    case other =>
      log.warn(s"FuturePerRequest - Tipo messaggio per request non gestito [${other.getClass.getName}]\nfrom ${sender()} \n${other}")

  }

  def actorError(e: DigitPaException): Unit

  def complete(m: => ToResponseMarshallable, actorClassId: String): Unit = {
    log.debug(s"FuturePerRequest - COMPLETE FutureActorPerRequest [$actorPathName]")
    val f: Future[akka.http.scaladsl.server.RouteResult] = requestContext.complete(m)
    f.onComplete(a => donePromise.complete(a))
    log.debug(s"FuturePerRequest - DESTROY FutureActorPerRequest [$actorPathName]")
    log.info(NodoLogConstant.logEnd(s"$actorClassId"))
    context.stop(context.self)
  }
}
