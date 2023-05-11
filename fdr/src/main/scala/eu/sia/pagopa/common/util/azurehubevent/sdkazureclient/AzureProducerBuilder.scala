package eu.sia.pagopa.common.util.azurehubevent.sdkazureclient

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown}
import akka.dispatch.MessageDispatcher
import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.eventhubs.{EventData, EventDataBatch, EventHubClientBuilder, EventHubProducerAsyncClient}
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.message.ReRequest
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.azurehubevent.AppObjectMapper
import eu.sia.pagopa.common.util.azurehubevent.Appfunction.{ReEventFunc, defaultOperation, sessionId}
import org.slf4j.MDC
import reactor.core.publisher.{Flux, Mono}

import java.time
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

object AzureProducerBuilder {

  def build()(implicit ec: ExecutionContext, system: ActorSystem, log: NodoLogger): ReEventFunc = {
    val eventConfigAzureSdkClient = system.settings.config.getConfig("azure-hub-event.azure-sdk-client.re-event")
    val eventHubName = eventConfigAzureSdkClient.getString("event-hub-name")
    val connectionString = eventConfigAzureSdkClient.getString("connection-string")
    val clientTimeoutMs = eventConfigAzureSdkClient.getLong("client-timeoput-ms")

    val reXmlLog = Try(system.settings.config.getBoolean("reXmlLog")).getOrElse(true)
    val reJsonLog = Try(system.settings.config.getBoolean("reJsonLog")).getOrElse(false)

    val reProducer: EventHubProducerAsyncClient =
      new EventHubClientBuilder().transportType(AmqpTransportType.AMQP_WEB_SOCKETS).connectionString(connectionString, eventHubName).buildAsyncProducerClient()

    val coordinatedShutdown = system.settings.config.getBoolean("coordinatedShutdown")
    if (coordinatedShutdown) {
      CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, s"reproducer-stop") { () =>
        log.info("Stopping reProducer")
        reProducer.close()
        Future.successful(Done)
      }
    } else {
      system.registerOnTermination(() => {
        log.info("Stopping reProducer")
        reProducer.close()
      })
    }

    (request: ReRequest, log: NodoLogger, data: ConfigData) => {
      val reProudcerEnabledAzureSdkClient = Try(DDataChecks.getConfigurationKeys(data, "azureSdkClientReEventEnabled").toBoolean).getOrElse(false)

      if (reProudcerEnabledAzureSdkClient) {
        val executionContext: MessageDispatcher = system.dispatchers.lookup("eventhub-dispatcher")
        Future(businessLogicForPublish(reProducer, data, request, clientTimeoutMs)(log))(executionContext) recoverWith { case e: Throwable =>
          log.error(e, "Producer sdk azure re event error")
          Future.failed(e)
        }
      } else {
        log.debug("CONFIGURATION_KEYS [azure-sdk-client.re-event.enabled]=false. Not forward to Azure")
        Future.successful(())
      }
      Future(defaultOperation(request, log, reXmlLog, reJsonLog, data))
    }
  }

  private def businessLogicForPublish(reProducer: EventHubProducerAsyncClient, ddataMap: ConfigData, request: ReRequest, clientTimeoutMs: Long)(implicit log: NodoLogger): Unit = {
    request.re.tipoEvento match {
      case Some(reTipoEvento) =>
        val key = ConfigUtil.getGdeConfigKey(reTipoEvento, request.re.sottoTipoEvento)
        ddataMap.gdeConfigurations.get(key) match {
          case Some(gdeConfig) =>
            if (gdeConfig.eventHubEnabled) {
              if (gdeConfig.eventHubPayloadEnabled) {
                publish(reProducer, Array(request).toSeq, clientTimeoutMs, log)
              } else {
                log.debug("Clean re payload")
                val newRe = request.re.copy(payload = None)
                publish(reProducer, Array(request.copy(re = newRe)).toSeq, clientTimeoutMs, log)
              }
            } else {
              log.debug(s"GDE_CONFIG key '$key', eventHub not enabled. Not forward to Azure")
            }
          case None =>
            log.debug(s"Cache GDE_CONFIG found but not found key '$key'. Forward to Azure")
            publish(reProducer, Array(request).toSeq, clientTimeoutMs, log)
        }
      case None =>
        log.debug(s"RE tipoEvento empty. Forward to Azure")
        publish(reProducer, Array(request).toSeq, clientTimeoutMs, log)
    }

  }

  private def publish(producer: EventHubProducerAsyncClient, reRequestSeq: Seq[ReRequest], clientTimeoutMs: Long, log: NodoLogger): Unit = {
    log.debug(s"create eventDatas")
    val eventDataSeq: Flux[EventData] = Flux.fromIterable(
      reRequestSeq
        .map(r => {
          val key = r.sessionId
          val eventData = new EventData(AppObjectMapper.objectMapper.writeValueAsString(r.re))
          eventData.getProperties.put(sessionId, key)
          MDC.getCopyOfContextMap.entrySet().asScala.map(a => eventData.getProperties.put(a.getKey, a.getValue))
          eventData
        })
        .asJava
    )

    val currentBatch: AtomicReference[EventDataBatch] = new AtomicReference(producer.createBatch().block())

    eventDataSeq
      .flatMap(eventData => {
        val msg = if (log.isDebugEnabled) {
          s"add to batch and send record \nheaders=[\n\t${eventData.getProperties.entrySet().iterator().asScala.map(a => s"${a.getKey}=${a.getValue}").mkString("\n\t")}\n] \nvalue=${eventData.getBodyAsString}"
        } else {
          ""
        }

        val batch: EventDataBatch = currentBatch.get()
        if (batch.tryAdd(eventData)) {
          log.debug(msg)
          Mono.empty[Void]
        } else {
          Mono.when({
            log.debug(s"eventData not inserted to batch beacause is full,send all event and create new batch")
            producer.send(batch)
            val eventDataBatch: Mono[EventDataBatch] = producer.createBatch()
            eventDataBatch.map(newBatch => {
              currentBatch.set(newBatch)
              if (!newBatch.tryAdd(eventData)) {
                val ex = new IllegalArgumentException(s"EventData is too large for an empty batch. Max size: ${newBatch.getMaxSizeInBytes}")
                log.error(
                  ex,
                  s"eventData tot inserted to batch beacause is too large, record \nheaders=[\n\t${eventData.getProperties.entrySet().iterator().asScala.map(a => s"${a.getKey}=${a.getValue}").mkString("\n\t")}\n] \nvalue=${eventData.getBodyAsString}"
                )
                throw ex
              } else {
                log.debug(msg)
                newBatch
              }
            })
          })
        }
      })
      .`then`()
      .doFinally(_ => {
        val batch = currentBatch.getAndSet(null)
        if (batch != null || batch.getCount > 0) {
          log.debug(s"send last event of batch")
          producer.send(batch).block(time.Duration.of(clientTimeoutMs, ChronoUnit.MILLIS))
        }
      })
      .subscribe(defaultConsumer(log), errorConsumer(log), completedRunnable(log))
  }

  private def defaultConsumer(log: NodoLogger): Consumer[_ >: Void] = { (f: Void) =>
    {
      if (log.isDebugEnabled) {
        log.debug(s"eventData send")
      }
    }
  }

  private def errorConsumer(log: NodoLogger): Consumer[_ >: Throwable] = { (ex: Throwable) =>
    {
      log.error(ex, s"Failed to produce: ${ex.getMessage}")
    }
  }

  private def completedRunnable(log: NodoLogger): Runnable = { () =>
    {
      log.debug("Completed sending events.")
    }
  }
}
