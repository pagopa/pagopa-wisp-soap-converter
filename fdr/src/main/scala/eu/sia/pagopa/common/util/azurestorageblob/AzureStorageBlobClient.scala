package eu.sia.pagopa.common.util.azurestorageblob

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.azure.core.credential.TokenCredential
import com.azure.core.util.BinaryData
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
      val containerName = system.settings.config.getString("azure-storage-blob.container-name")
      val connectionString = system.settings.config.getString("azure-storage-blob.connection-string")
      val defaultCredential: TokenCredential = new DefaultAzureCredentialBuilder().build()

      val blobServiceClient = new BlobServiceClientBuilder()
        .credential(defaultCredential)
        .connectionString(connectionString)
        .buildClient()

      val containerClient = blobServiceClient.getBlobContainerClient(containerName)

      (fileName: String, inputStream: InputStream, log: NodoLogger) => {
        val executionContext: MessageDispatcher = system.dispatchers.lookup("eventhub-dispatcher")
        Future(containerClient.getBlobClient(fileName).upload(BinaryData.fromStream(inputStream)))(executionContext) recoverWith {
          case e: Throwable =>
            log.error(e, "Error calling azure-storage-blob")
            Future.failed(e)
        }
      }
    } else {
      (fileName: String, inputStream: InputStream, log: NodoLogger) => {
        log.debug("config-app [azure-storage-blob.enabled]=false. Not enable to storage blob")
        Future.successful(())
      }
    }
  }
}
