package it.gov.pagopa.tests

import akka.actor.{ActorSystem, ClassicActorSystemProvider, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.{TimeToLive, Times}
import org.mockserver.model.RequestDefinition

class MockserverExtensionImpl extends Extension {
  private var mockserver:ClientAndServer = null
  def when(rd:RequestDefinition) = mockserver.when(rd)
  def when(rd:RequestDefinition,times:Times) = mockserver.when(rd,times)
  def when(rd:RequestDefinition,times:Times,ttl:TimeToLive) = mockserver.when(rd,times,ttl)
  def when(rd:RequestDefinition,times:Times,ttl:TimeToLive,priority:Integer) = mockserver.when(rd,times,ttl,priority)
  def set(ms:ClientAndServer) = mockserver = ms
}

object MockserverExtension extends ExtensionId[MockserverExtensionImpl] with ExtensionIdProvider {
  //The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup = MockserverExtension

  //This method will be called by Akka
  // to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new MockserverExtensionImpl

  /**
   * Java API: retrieve the Count extension for the given system.
   */
  override def get(system: ActorSystem): MockserverExtensionImpl = super.get(system)
  override def get(system: ClassicActorSystemProvider): MockserverExtensionImpl = super.get(system)
}