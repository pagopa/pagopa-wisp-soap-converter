package eu.sia.pagopa.common.actor

import akka.actor.Actor
import eu.sia.pagopa.common.util.NodoLogger

trait NodoLogging { this: Actor =>

  implicit val log: NodoLogger = new NodoLogger(akka.event.Logging.apply(this))

}
