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
import org.slf4j.MDC

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class CosmosBuilder() {

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: AppLogger): ReEventFunc = {

    val cosmosContainer = getClient(system.settings.config)
    val executionContext: MessageDispatcher = system.dispatchers.lookup("azurestorage-dispatcher")

    val reXmlLog = Try(system.settings.config.getBoolean("reXmlLog")).getOrElse(true)
    val reJsonLog = Try(system.settings.config.getBoolean("reJsonLog")).getOrElse(false)

    (request: ReRequest, log: AppLogger, data: ConfigData) => {
      Future.sequence(
        Seq(
          Future(defaultOperation(request, log, reXmlLog, reJsonLog, data)),
          ((for {
            _ <- Future.successful(())
            item = reRequestToReEvent(request)
            _ = cosmosContainer.createItem(item)
          } yield ())(executionContext)) recoverWith {
            case e: Throwable =>
              log.error(e, "Error calling azure-cosmos for events")
              Future.failed(e)
          }
        )
      ).flatMap(_ => Future.successful(()))

    }
  }

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

  def reRequestToReEvent(request: ReRequest): ReEventEntity = {
    val compressedpayload = request.re.requestPayload.map(compress)
    val base64payload = compressedpayload.map(cp => Base64.getEncoder.encodeToString(cp))

    val fault: Option[(String, Option[String], Option[String])] = request.re.requestPayload.flatMap(p => Appfunction.getFaultFromXml(new String(p, Constant.UTF_8)))
    ReEventEntity(
      id = request.re.uniqueId,
      partitionKey = request.re.insertedTimestamp.toString.substring(0, 10),
      operationId = request.sessionId,
      insertedTimestamp = request.re.insertedTimestamp,
      eventCategory = request.re.eventCategory.toString,
      status = request.re.status.orNull,
      outcome = request.re.outcome.toString,
      httpMethod = request.reExtra.flatMap(_.httpMethod).orNull,
      httpUri = request.reExtra.flatMap(_.uri).orNull,
      httpStatusCode = request.reExtra.flatMap(_.statusCode).map(d => new java.lang.Integer(d)).orNull,
      executionTimeMs = request.reExtra.flatMap(_.elapsed).map(d => new java.lang.Long(d)).orNull,
      requestHeaders = request.reExtra.map(_.headers.mkString(",")).orNull,
      responseHeaders = ???,
      requestPayload = base64payload.orNull,
      responsePayload = ???,
      businessProcess = request.re.businessProcess.get,
      operationErrorCode = fault.flatMap(_._3).orNull,
      operationErrorDetail = fault.flatMap(_._2).orNull,
      sessionId = MDC.get(Constant.MDCKey.SESSION_ID),
      cartId = request.re.cartId.orNull,
      iuv = request.re.iuv.orNull,
      noticeNumber = request.re.noticeNumber.orNull,
      domainId = request.re.domainId.orNull,
      ccp = request.re.ccp.orNull,
      psp = request.re.psp.orNull,
      station = request.re.station.orNull,
      channel = request.re.channel.orNull,
      info = request.re.info.orNull
    )
  }

  def compress(payload: Array[Byte]): Array[Byte] = {
    val bais = new ByteArrayOutputStream(payload.length)
    val gzipOut = new GZIPOutputStream(bais)
    gzipOut.write(payload)
    gzipOut.close()
    val compressed = bais.toByteArray
    bais.close()
    compressed
  }

}
