package eu.sia.pagopa.common.actor

import akka.actor.{ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.{SSLConfigFactory, SSLConfigSettings}

object NodoAkkaSSLConfig extends ExtensionId[AkkaSSLConfig] with ExtensionIdProvider {

  override def get(system: ActorSystem): AkkaSSLConfig = super.get(system)
  //  override def get(system: ClassicActorSystemProvider): AkkaSSLConfig = super.get(system)
  def apply()(implicit system: ActorSystem): AkkaSSLConfig = super.apply(system)

  override def lookup() = AkkaSSLConfig

  override def createExtension(system: ExtendedActorSystem): AkkaSSLConfig =
    new AkkaSSLConfig(system, defaultSSLConfigSettings(system))

  def defaultSSLConfigSettings(system: ActorSystem): SSLConfigSettings = {
    //    val akkaOverrides = system.settings.config.getConfig("akka.ssl-config")
    val akkaOverrides = system.settings.config.getConfig("httpactor.ssl-config")
    val defaults = system.settings.config.getConfig("ssl-config")
    SSLConfigFactory.parse(akkaOverrides.withFallback(defaults))
  }

}
