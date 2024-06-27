package it.gov.pagopa.common.util.azure.cosmos

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.azure.cosmos.{CosmosClientBuilder, CosmosContainer}
import com.typesafe.config.Config
import it.gov.pagopa.common.message.ReRequest
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction
import it.gov.pagopa.common.util.azure.Appfunction.{ReEventFunc, defaultOperation}

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class CosmosBuilder() {

  def getClient(config: Config): CosmosContainer = {
    val endpoint = config.getString("azure-cosmos-events.endpoint")
    val key = config.getString("azure-cosmos-events.key")
    val database = config.getString("azure-cosmos-events.db-name")
    val container = config.getString("azure-cosmos-events.table-name")

    new CosmosClientBuilder()
      .endpoint(endpoint)
      .key(key)
      .buildClient().getDatabase(database).getContainer(container);
  }

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: AppLogger): ReEventFunc = {

    val cosmosContainer = getClient(system.settings.config)
    val executionContext: MessageDispatcher = system.dispatchers.lookup("azurestorage-dispatcher")

    val reXmlLog = Try(system.settings.config.getBoolean("reXmlLog")).getOrElse(true)
    val reJsonLog = Try(system.settings.config.getBoolean("reJsonLog")).getOrElse(false)

    (request: ReRequest, log: AppLogger, data: ConfigData) => {
      Future.sequence(
        Seq(
          Future(defaultOperation(request, log, reXmlLog, reJsonLog, data)),
          ((for{
            _ <- Future.successful(())
            item = reRequestToReEvent(request)
            _ = cosmosContainer.createItem(item)
          } yield ())(executionContext))recoverWith {
            case e: Throwable =>
              log.error(e, "Error calling azure-cosmos for events")
              Future.failed(e)
          }
        )
      ).flatMap(_=>Future.successful(()))

    }
  }

  def compress(payload : Array[Byte]): Array[Byte] = {
    val bais = new ByteArrayOutputStream(payload.length)
    val gzipOut = new GZIPOutputStream(bais)
    gzipOut.write(payload)
    gzipOut.close()
    val compressed = bais.toByteArray
    bais.close()
    compressed
  }
  def reRequestToReEvent(request: ReRequest): ReEventEntity = {
    val compressedpayload = request.re.payload.map(compress)
    val base64payload = compressedpayload.map(cp=>Base64.getEncoder.encodeToString(cp))

    val fault: Option[(String, Option[String], Option[String])] = request.re.payload.flatMap(p=>Appfunction.getFaultFromXml(new String(p, Constant.UTF_8)))
    ReEventEntity(
      request.re.uniqueId,
      request.re.insertedTimestamp.toString.substring(0,10),
      null,
      request.sessionId,
      null,
      request.re.componente.toString,
      request.re.insertedTimestamp,
      request.re.categoriaEvento.toString,
      request.re.sottoTipoEvento.toString,
      request.reExtra.flatMap(_.callType).map(_.toString).orNull,
      request.re.esito.toString,
      request.reExtra.flatMap(_.httpMethod).orNull,
      request.reExtra.flatMap(_.uri).orNull,
      request.reExtra.map(_.headers.mkString(",")).orNull,
      request.reExtra.flatMap(_.callRemoteAddress).orNull,
      request.reExtra.flatMap(_.statusCode).map(d => new java.lang.Integer(d)).orNull,
      request.reExtra.flatMap(_.elapsed).map(d => new java.lang.Long(d)).orNull,
      base64payload.orNull, //comprimere
      base64payload.map(_.length).map(d => new java.lang.Integer(d)).orNull,
      request.re.businessProcess.get,
      if(fault.isDefined)"Failed" else "Success",
      fault.map(_._1).orNull,
      fault.flatMap(_._2).orNull,
      fault.flatMap(_._3).orNull,
      request.re.tipoEvento.orNull,
      request.re.sessionIdOriginal.orNull,
      request.re.idCarrello.orNull,
      request.re.iuv.orNull,
      request.re.noticeNumber.orNull,
      request.re.idDominio.orNull,
      request.re.ccp.orNull,
      request.re.psp.orNull,
      request.re.stazione.orNull,
      request.re.canale.orNull,
      request.re.status.orNull,
      request.re.info.orNull
    )
  }

}
