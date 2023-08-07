package eu.sia.pagopa.common.util.queueclient

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.azure.core.util.BinaryData
import com.azure.storage.queue.{QueueClient, QueueClientBuilder, QueueServiceClientBuilder}
import eu.sia.pagopa.common.util.NodoLogger
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.QueueAddFunc

import scala.concurrent.{ExecutionContext, Future}

object AzureQueueClient {

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: NodoLogger): QueueAddFunc = {
    log.info("Starting Azure Queue for re-add FdR ...")
    val name = system.settings.config.getString("azure-queue.name")
    val connectionString = system.settings.config.getString("azure-queue.connection-string")

    val queueClient = new QueueClientBuilder()
      .queueName(name)
      .connectionString(connectionString)

    val containerClient = queueClient.buildClient()

    (fdr: String, pspId: String, fdrMessage: String, log: NodoLogger) => {
      val executionContext: MessageDispatcher = system.dispatchers.lookup("queueAdd-dispatcher")
      log.info(s"Send message. queuename=[$name], pspId=[$pspId], fdr=[$fdr]")
      Future(containerClient.sendMessage(fdrMessage))(executionContext) recoverWith {
        case e: Throwable =>
          log.error(e, "Error calling azure-storage-blob")
          Future.failed(e)
      }
    }
  }
}
