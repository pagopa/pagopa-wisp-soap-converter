package eu.sia.pagopa.common.util.azurestorageblob

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobServiceClientBuilder
import eu.sia.pagopa.common.util.NodoLogger
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.ContainerBlobFunc

import java.io.InputStream
import scala.concurrent.{ExecutionContext, Future}

object AzureStorageBlobClient {

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: NodoLogger): ContainerBlobFunc = {
    val azureStorageBlobEnabled = system.settings.config.getBoolean("azure-storage-blob.enabled")

    if( azureStorageBlobEnabled ) {
      val azureStorageBlobContainerName = system.settings.config.getString("azure-storage-blob.containerName")
      val azureStorageBlobContainerEndpoint = system.settings.config.getString("azure-storage-blob.endpoint")
      val defaultCredential = new DefaultAzureCredentialBuilder().build()

      val blobServiceClient = new BlobServiceClientBuilder()
        .endpoint(azureStorageBlobContainerEndpoint)
        .credential(defaultCredential)
        .buildClient()

      val blobClient = blobServiceClient.getBlobContainerClient(azureStorageBlobContainerName)

      (fileName: String, inputStream: InputStream, log: NodoLogger) => {
        val executionContext: MessageDispatcher = system.dispatchers.lookup("eventhub-dispatcher")
        Future(blobClient.getBlobClient(fileName).upload(inputStream))(executionContext) recoverWith { case e: Throwable =>
          log.error(e, "Error azure-storage-blob")
          Future.failed(e)
        }
      }
    } else {
      (fileName: String, inputStream: InputStream, log: NodoLogger) => {
        log.debug("CONFIGURATION_KEYS [azure-storage-blob.enabled]=false. Not storage blob")
        Future.successful(())
      }
    }
  }
}
