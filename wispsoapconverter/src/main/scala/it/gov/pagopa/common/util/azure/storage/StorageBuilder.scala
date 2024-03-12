package it.gov.pagopa.common.util.azure.storage

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.azure.core.util.BinaryData
import com.azure.data.tables.models.TableEntity
import com.azure.data.tables.{TableClient, TableServiceClient, TableServiceClientBuilder}
import com.azure.storage.blob.{BlobContainerClient, BlobServiceClient, BlobServiceClientBuilder}
import com.typesafe.config.Config
import it.gov.pagopa.common.message.ReRequest
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc

import scala.concurrent.{ExecutionContext, Future}

object StorageBuilder{
  private val BLOB_REF:String="payload_ref_id"
  private val BLOB_LEN:String="payload_length"
}
case class StorageBuilder() {

  def getClients(config:Config): (TableClient, BlobContainerClient) = {
    val storageConnectionString = config.getString("azure-storage.connection-string")
    val blobcontainerName = config.getString("azure-storage.blob-name")
    val tableName = config.getString("azure-storage.table-name")
    val blobServiceClient: BlobServiceClient = new BlobServiceClientBuilder()
      .connectionString(storageConnectionString)
      .buildClient()
    val tableServiceClient: TableServiceClient = new TableServiceClientBuilder()
      .connectionString(storageConnectionString)
      .buildClient();

    val blobContainerClient = blobServiceClient.getBlobContainerClient(blobcontainerName)
    val tableContainerClient = tableServiceClient.getTableClient(tableName)
    (tableContainerClient,blobContainerClient)
  }

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: AppLogger): ReEventFunc = {

    val (tableContainerClient,blobContainerClient) = getClients(system.settings.config)
    val executionContext: MessageDispatcher = system.dispatchers.lookup("azurestorage-dispatcher")

    (request: ReRequest, log: AppLogger) => {
      log.info(s"${request.re}")
      ((for{
        _ <- Future.successful(())
        payloadSize = request.re.payload.map(pl=>{
          val zippedPayload = Util.zipContent(pl);
          blobContainerClient.getBlobClient(request.re.uniqueId).upload(BinaryData.fromBytes(zippedPayload))
          zippedPayload.length
        })
        tableEntity = new TableEntity(request.re.insertedTimestamp.toString.substring(0,10), request.re.uniqueId)
        _ = tableEntity.setProperties(request.re.toProperties())
        _ = {
          payloadSize.map(ps=>{
            tableEntity.addProperty(StorageBuilder.BLOB_REF,request.re.uniqueId)
            tableEntity.addProperty(StorageBuilder.BLOB_LEN,ps)
          })
        }
        _ = tableContainerClient.createEntity(tableEntity)
      } yield ())(executionContext))recoverWith {
        case e: Throwable =>
          log.error(e, "Error calling azure-storage-blob")
          Future.failed(e)
      }
    }
  }

}
