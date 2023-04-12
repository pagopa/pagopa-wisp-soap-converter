package eu.sia.pagopa.common.actor

import akka.actor.{ Actor, DeadLetter, Props }
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{ DigitPaErrorCodes, DigitPaException }
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util.Constant

object DeadLetterMonitorActor {
  val actorClassId = Constant.KeyName.DEAD_LETTER_MONITOR
  val actorRole: (String, Props) = actorClassId -> Props(classOf[DeadLetterMonitorActor])
}

class DeadLetterMonitorActor extends Actor with NodoLogging {

  context.system.eventStream.subscribe(self, classOf[DeadLetter])

  val name: String = self.path.name

  //noinspection ScalaStyle
  def receive: PartialFunction[Any, Unit] = { case DeadLetter(message: BaseMessage, sender, recipient) =>
    val mex = s"""Could not deliver message
                   |sessionId[${message.sessionId}]
                   |type     [${message.getClass.getSimpleName}]
                   |from     [${sender.path.name}]
                   |to       [${recipient.path.name}]""".stripMargin

    val ex = new Exception(mex)

    message match {
      case _: ReRequest =>
      case r: SoapRequest =>
        sender ! SoapResponse(r.sessionId, None, StatusCodes.OK.intValue, None, r.testCaseId, Some(ex))
      case r: WorkRequest =>
        sender ! WorkResponse(r.sessionId, r.testCaseId, r.key, Some(DigitPaErrorCodes.PPT_SYSTEM_ERROR))
      case r: ScheduleJobRequest =>
        sender ! ScheduleJobResponse(r.sessionId, SchedulerStatus.KO, Some("Dead letter"), r.testCaseId)
      case _ =>
        log.warn(mex)

    }

  }

}
