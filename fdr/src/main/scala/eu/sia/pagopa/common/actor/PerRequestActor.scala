package eu.sia.pagopa.common.actor

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, ReceiveTimeout, SupervisorStrategy}
import akka.stream.Materializer
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

trait PerRequestActor extends Actor with NodoLogging {
  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  implicit val actorPathName: String = self.path.name
  implicit val actorSystem: ActorSystem = context.system

  val actorProps: ActorProps
  val repositories: Repositories

  implicit val actorMaterializer: Materializer = actorProps.actorMaterializer

  def actorClassId: String = actorProps.actorClassId
  val ddataMap: ConfigData = actorProps.ddataMap
  val reEventFunc: ReEventFunc = actorProps.reEventFunc
  val actorUtility: ActorUtility = actorProps.actorUtility

  val httpConnectTimeout: FiniteDuration = context.system.settings.config.getInt("config.http.connect-timeout").seconds

  val BUNDLE_IDLE_TIMEOUT: FiniteDuration =
    FiniteDuration(context.system.settings.config.getInt("bundleTimeoutSeconds"), TimeUnit.SECONDS)
  context.setReceiveTimeout(BUNDLE_IDLE_TIMEOUT)

  val actorName: String = self.path.name
  log.debug(s"PerRequestActor - This actor [$actorName] will die in [${BUNDLE_IDLE_TIMEOUT.toString}]")

  override val supervisorStrategy: SupervisorStrategy = {
    OneForOneStrategy() { case e: Throwable =>
      val dpe =
        DigitPaException("PerRequestActor - Actor SupervisorStrategy Error", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      log.error(dpe, dpe.getMessage)
      actorError(dpe)
      Stop
    }
  }

  context.become(receive orElse baseReceive)
  def baseReceive: Receive = {
    case ReceiveTimeout =>
      val dpe = exception.DigitPaException("PerRequestActor - Actor Internal Timeout Error", DigitPaErrorCodes.PPT_SYSTEM_ERROR)
      log.error(dpe, dpe.getMessage)
      actorError(dpe)
      complete()
    case other =>
      log.warn(s"PerRequestActor - Message type for unhandled request [${other.getClass.getName}]\nfrom ${sender()} \n${other}")
  }

  def actorError(e: DigitPaException): Unit

  def complete(fn: () => Unit = () => ()): Unit = {
    log.debug(s"PerRequestActor - COMPLETE ActorPerRequest [$actorPathName]")
    fn()
    context.stop(context.self)
  }

  def askBundle[T, S](actor: ActorRef, req: T)(implicit tag: ClassTag[S]): Future[S] = {
    import akka.pattern.ask
    actor.ask(req)(BUNDLE_IDLE_TIMEOUT).mapTo[S]
  }

}
