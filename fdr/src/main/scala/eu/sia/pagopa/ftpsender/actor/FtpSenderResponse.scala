package eu.sia.pagopa.ftpsender.actor

import akka.actor.ActorRef
import eu.sia.pagopa.common.exception.DigitPaException
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.util._
import org.slf4j.MDC

trait FtpSenderResponse {

  def actorError(replyTo: ActorRef, req: FTPRequest, dpe: DigitPaException): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val response = FTPResponse(req.sessionId, Some(dpe.getMessage), None)
    replyTo ! response
    //MDC.remove(Constant.MDCKey.SESSION_ID)
  }

}
