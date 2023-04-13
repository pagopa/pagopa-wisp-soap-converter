package eu.sia.pagopa.common.actor
import akka.Done
import akka.actor.{Actor, ActorContext, ActorRef, Props, Terminated}
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ReEventFunc
import eu.sia.pagopa.ftpsender.actor.FtpSenderActorPerRequest
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

final case class PrimitiveActor(repositories: Repositories, actorProps: ActorProps) extends BaseActor {

  val actors = new AtomicInteger(0)

  def createActorPerRequestAndTell[T <: BaseMessage](request: T, actorClassId: String, props: Props)(implicit log: NodoLogger, context: ActorContext): ActorRef = {
    val pr = s"pr-$actorClassId-${UUID.randomUUID().toString}-${request.sessionId}"
    log.debug(s"CREATE ActorPerRequest [$pr]")
    val perrequest = context.actorOf(props, pr)
    log.debug(s"TELL ActorPerRequest [$pr]")
    perrequest.forward(request)
    perrequest
  }

  def createActorPerRequest[T](sender: ActorRef, request: BaseMessage, clazz: Class[T], actorClassId: String, extraData: Option[String] = None): Unit = {

    MDC.put(Constant.MDCKey.ACTOR_CLASS_ID, actorClassId)

    log.info(s"creazione actor per request ${actorClassId}${extraData.map(d => s"[$d]").getOrElse("")} of class ${clazz.getSimpleName}")
    Try({
      val a = createActorPerRequestAndTell(request, BootstrapUtil.actorClassId(clazz), Props(clazz, repositories, actorProps.copy(actorClassId = actorClassId)))(log, context)
      context.watch(a)
      actors.incrementAndGet()
    }).recover { case e: Throwable =>
      log.error(e, s"Errore creazione ActorPerRequest ${actorClassId}")
      sender ! "errore" //FIXME rispondere errore xml/json
    //          actorError(replyTo, soapRequet, ddataMap, DigitPaException("Errore creazione ActorPerRequest", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), None)
    }
  }

  def receive: Actor.Receive = {
    case Done =>
      sender() ! (actors.get() == 0)
    case Terminated(_) =>
      actors.decrementAndGet()
    case workRequest: WorkRequest =>
      val clazz = Primitive.getActorClass(workRequest.jobName)
      createActorPerRequest(sender(), workRequest, clazz, workRequest.jobName, workRequest.key)
    case restRequest: RestRequest =>
      val clazz = Primitive.getActorClass(restRequest.primitive)
      createActorPerRequest(sender(), restRequest, clazz, restRequest.primitive)
    case soapRequest: SoapRequest =>
      val clazz = Primitive.getActorClass(soapRequest.primitive)
      createActorPerRequest(sender(), soapRequest, clazz, soapRequest.primitive)
    case ftpRequest: FTPRequest =>
      val clazz = classOf[FtpSenderActorPerRequest]
      createActorPerRequest(sender(), ftpRequest, clazz, Constant.KeyName.FTP_SENDER)
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
  val repositories: Repositories
  val ddataMap: ConfigData = actorProps.ddataMap
  val reEventFunc: ReEventFunc = actorProps.reEventFunc

}
