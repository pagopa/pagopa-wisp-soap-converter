package it.gov.pagopa.common.actor

import akka.Done
import akka.actor.{Actor, ActorContext, ActorRef, Props, Terminated}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

final case class PrimitiveActor(cosmos:CosmosRepository,
                                actorProps: ActorProps) extends BaseActor {

  val actors = new AtomicInteger(0)

  def createActorPerRequestAndTell[T <: BaseMessage](request: T, actorClassId: String, props: Props)(implicit log: AppLogger, context: ActorContext): ActorRef = {
    val pr = s"pr-$actorClassId-${UUID.randomUUID().toString}-${request.sessionId}"
    log.debug(s"CREATE ActorPerRequest [$pr]")
    val perrequest = context.actorOf(props, pr)
    log.debug(s"TELL ActorPerRequest [$pr]")
    perrequest.forward(request)
    perrequest
  }

  def createActorPerRequest[T](sender: ActorRef, request: BaseMessage, clazz: Class[T], actorClassId: String, extraData: Option[String] = None): Unit = {

    MDC.put(Constant.MDCKey.ACTOR_CLASS_ID, actorClassId)

    log.debug(s"Creating actor per request ${actorClassId}${extraData.map(d => s"[$d]").getOrElse("")} of class ${clazz.getSimpleName}")
    Try({
      val a = createActorPerRequestAndTell(request, BootstrapUtil.actorClassId(clazz), Props(clazz, cosmos,
        actorProps.copy(actorClassId = actorClassId)))(log, context)
      context.watch(a)
      actors.incrementAndGet()
    }).recover { case e: Throwable =>
      log.error(e, s"Error creating ActorPerRequest ${actorClassId}")
      sender ! "errore" //FIXME rispondere errore xml/json
    //          actorError(replyTo, soapRequet, ddataMap, DigitPaException("Errore creazione ActorPerRequest", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), None)
    }
  }

  def receive: Actor.Receive = {
    case Done =>
      sender() ! (actors.get() == 0)
    case Terminated(_) =>
      actors.decrementAndGet()
    case soapRequest: SoapRequest =>
      val clazz = Primitive.getActorClass(soapRequest.primitive, false)
      createActorPerRequest(sender(), soapRequest, clazz, soapRequest.primitive)
    case x =>
      log.error(s"""########################
          |unmanaged message type ${x.getClass}
          |########################""".stripMargin)
  }

}
trait BaseActor extends Actor with NodoLogging {

  implicit val executionContext: ExecutionContextExecutor = context.dispatcher
  val actorClassId: String = this.getClass.getSimpleName

  val actorProps: ActorProps
//  val repositories: Repositories
  val ddataMap: ConfigData = actorProps.ddataMap
  val reEventFunc: ReEventFunc = actorProps.reEventFunc

}
