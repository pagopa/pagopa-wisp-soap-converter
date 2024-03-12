package it.gov.pagopa.common.actor

import akka.actor.{Actor, DeadLetter, Props}
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.common.exception.DigitPaErrorCodes
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.util.Constant

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

    message match {
      case _: ReRequest =>
      case r: SoapRequest =>
        sender ! SoapResponse(r.sessionId, "dead letter", StatusCodes.OK.intValue, None, r.testCaseId)
      case _ =>
        log.warn(mex)
    }
  }
}
