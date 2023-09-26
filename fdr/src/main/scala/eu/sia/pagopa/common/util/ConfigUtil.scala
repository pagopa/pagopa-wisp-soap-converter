package eu.sia.pagopa.common.util

import akka.actor.ActorSystem
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.model.headers.{HttpEncodings, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.introspect.ScalaAnnotationIntrospectorModule
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.util.Constant.HEADER_SUBSCRIPTION_KEY
import it.pagopa.config.{CacheVersion, Service}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try




object ConfigUtil {

  private var reloading: Boolean = false

  //https://github.com/FasterXML/jackson-module-scala/wiki/FAQ#deserializing-optionint-seqint-and-other-primitive-challenges
  //explicitly setting type of field for Option<primitive type>,otherwise results in boxed error
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[Service], "targetPort", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.pagopa.config.Proxy], "proxyPort", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.pagopa.config.Redirect], "port", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.pagopa.config.StationCreditorInstitution], "applicationCode", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.pagopa.config.StationCreditorInstitution], "auxDigit", classOf[Long])
  ScalaAnnotationIntrospectorModule.registerReferencedValueType(classOf[it.pagopa.config.StationCreditorInstitution], "segregationCode", classOf[Long])

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

  private def callApiConfig(path:String,httpsConnectionContext: HttpsConnectionContext)(implicit log: NodoLogger, system:ActorSystem,ex: ExecutionContext): Future[HttpResponse] = {
    val apiConfigUrl = system.settings.config.getString("apiConfigCache.url")
    val subKey = Try(system.settings.config.getString("apiConfigCache.subscriptionKey")).getOrElse("n/d")
    val timeout = Try(system.settings.config.getInt("apiConfigCache.timeout")).getOrElse(30)
    import scala.concurrent.duration._
    val settings = ConnectionPoolSettings(system).withConnectionSettings(ClientConnectionSettings(system).withIdleTimeout(timeout.seconds).withConnectingTimeout(5.seconds))
    val uri = s"$apiConfigUrl$path"
    log.info(s"calling ApiConfigCache on [$uri]")
    for{
      req <- Future.fromTry(Try(HttpRequest(uri = uri, headers = Seq(RawHeader(HEADER_SUBSCRIPTION_KEY, subKey)))))
      res <- if (uri.startsWith("https")) {
        Http().singleRequest(req, connectionContext = httpsConnectionContext, settings = settings)
      } else {
        Http().singleRequest(req, settings = settings)
      }
    } yield res
  }

  def getConfigHttp(httpsConnectionContext:HttpsConnectionContext)(implicit log: NodoLogger, system:ActorSystem, ex: ExecutionContext) = {
    (for {
      res <- callApiConfig(s"", httpsConnectionContext)
      resDec = decodeResponse(res)
      resBody <- Unmarshaller.stringUnmarshaller(resDec.entity)
      d = mapper.readValue(resBody, classOf[ConfigData])
    } yield Some(d)).recover({
      case e =>
        log.error(e,"getConfigHttp error")
        None
    })
  }

  def getConfigVersion(httpsConnectionContext: HttpsConnectionContext)(implicit log: NodoLogger, system: ActorSystem, ex: ExecutionContext) = {
    (for {
      res <- callApiConfig(s"/id", httpsConnectionContext)
      resBody <- Unmarshaller.stringUnmarshaller(res.entity)
      d = mapper.readValue(resBody,classOf[CacheVersion])
    } yield Some(d.version)).recover({
      case _ =>
        None
    })
  }

  def refreshConfigHttp(actorProps:ActorProps,manual:Boolean)(implicit log: NodoLogger, system: ActorSystem, ex: ExecutionContext): Future[ConfigData] = {
    log.info(s"${if(manual) "manual" else "automatic"} refresh config")
    if(!reloading){
      reloading = true
      (for {
        res <- callApiConfig(s"?refresh=true", actorProps.httpsConnectionContext)
        resDec = decodeResponse(res)
        resBody <- Unmarshaller.stringUnmarshaller(resDec.entity)
        d = mapper.readValue(resBody, classOf[ConfigData])
        _ = log.info("force refresh config done")
        _ = reloading = false
      } yield d).recoverWith({
        case e=>
          reloading = false
          Future.failed(new RuntimeException("reloading in progress"))
      })
    } else {
      Future.failed(new RuntimeException("reloading in progress"))
    }

  }

  def getGdeConfigKey(primitiva: String, primitivaType: String): String = {
    s"${primitiva}_$primitivaType".toUpperCase
  }

}
