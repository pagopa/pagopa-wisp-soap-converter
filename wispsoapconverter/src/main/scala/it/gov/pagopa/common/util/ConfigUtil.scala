package it.gov.pagopa.common.util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.model.headers.{HttpEncodings, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.introspect.ScalaAnnotationIntrospectorModule
import it.gov.pagopa.common.util.Constant.HEADER_SUBSCRIPTION_KEY
import it.gov.pagopa.config.{CacheVersion, ConfigDataV1, Service}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object ConfigUtil {
  type ConfigData = ConfigDataV1
  private var reloading: Boolean = false

  //https://github.com/FasterXML/jackson-module-scala/wiki/FAQ#deserializing-optionint-seqint-and-other-primitive-challenges
  //explicitly setting type of field for Option<primitive type>,otherwise results in boxed error
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[Service], "targetPort", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.gov.pagopa.config.Proxy], "proxyPort", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.gov.pagopa.config.Redirect], "port", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.gov.pagopa.config.StationCreditorInstitution], "applicationCode", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.gov.pagopa.config.StationCreditorInstitution], "auxDigit", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.gov.pagopa.config.StationCreditorInstitution], "segregationCode", classOf[Long])

  private val mapper = new ObjectMapper().findAndRegisterModules()
  mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE)
  mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
  mapper.setVisibility(PropertyAccessor.CREATOR, Visibility.ANY)

  def decodeResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip =>
        Coders.Gzip
      case HttpEncodings.deflate =>
        Coders.Deflate
      case HttpEncodings.identity =>
        Coders.NoCoding
      case other =>
        Coders.NoCoding
    }

    decoder.decodeMessage(response)
  }

  private def callApiConfig(path:String,params:String="")(implicit log: AppLogger, system:ActorSystem, ex: ExecutionContext): Future[HttpResponse] = {
    val apiConfigUrl = system.settings.config.getString("apiConfigCache.url")
    val subKey = Try(system.settings.config.getString("apiConfigCache.subscriptionKey")).getOrElse("n/d")
    val timeout = Try(system.settings.config.getInt("apiConfigCache.timeout")).getOrElse(30)
    import scala.concurrent.duration._
    val settings = ConnectionPoolSettings(system).withConnectionSettings(ClientConnectionSettings(system).withIdleTimeout(timeout.seconds).withConnectingTimeout(5.seconds))
    val uri = s"$apiConfigUrl$path$params"
    log.info(s"calling ApiConfigCache on [$uri]")
    for{
      req <- Future.fromTry(Try(HttpRequest(uri = uri, headers = Seq(RawHeader(HEADER_SUBSCRIPTION_KEY, subKey)))))
      res <- Http().singleRequest(req, settings = settings)
    } yield res
  }

  def getConfigHttp()(implicit log: AppLogger, system:ActorSystem, ex: ExecutionContext) = {
    val keys = system.settings.config.getString("apiConfigCache.keys")
    for {
      res <- callApiConfig(s"",keys)
      resDec = decodeResponse(res)
      resBody <- Unmarshaller.stringUnmarshaller(resDec.entity)
      d = mapper.readValue(resBody, classOf[ConfigData])
    } yield d
  }

  def getConfigVersion()(implicit log: AppLogger, system: ActorSystem, ex: ExecutionContext) = {
    (for {
      res <- callApiConfig(s"/id")
      resBody <- Unmarshaller.stringUnmarshaller(res.entity)
      d = mapper.readValue(resBody,classOf[CacheVersion])
    } yield Some(d.version)).recover({
      case e =>
        None
    })
  }

}
