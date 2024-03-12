package it.gov.pagopa.common.actor

import akka.actor.Actor
import it.gov.pagopa.common.util.AppLogger

trait NodoLogging { this: Actor =>

  implicit val log: AppLogger = new AppLogger(akka.event.Logging.apply(this))

}
