package eu.sia.pagopa.testutil

import akka.actor.Actor
import eu.sia.pagopa.common.actor.NodoLogging
import eu.sia.pagopa.common.message._

case class MockActor() extends Actor with NodoLogging {

  override def receive: Receive = {
    case r: FTPRequest =>
      log.info(s"FTPRequest ricevuta ${r.sessionId}")
      sender() ! FTPResponse(r.sessionId, r.testCaseId, None)
    case r: WispAdapterChiediSceltaRequest =>
      sender() ! WispAdapterChiediSceltaResponse(r.sessionId, r.testCaseId, None)
    case r: WorkRequest =>
      sender() ! WorkResponse(r.sessionId, r.testCaseId, None)
  }
}
