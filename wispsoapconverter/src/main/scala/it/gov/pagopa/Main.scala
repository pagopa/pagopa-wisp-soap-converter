package it.gov.pagopa

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown, Props}
import akka.event.Logging
import akka.http.scaladsl.HttpExt
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import io.github.mweirauch.micrometer.jvm.extras.{ProcessMemoryMetrics, ProcessThreadMetrics}
import io.micrometer.core.instrument.binder.jvm.{ClassLoaderMetrics, JvmGcMetrics, JvmMemoryMetrics, JvmThreadMetrics}
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import it.gov.pagopa.actors.ApiConfigActor
import it.gov.pagopa.common.actor._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.Appfunction.ReEventFunc
import it.gov.pagopa.common.util.azure.storage.StorageBuilder
import it.gov.pagopa.common.util.web.NodoRoute
import org.slf4j.MDC
import scalaz.BuildInfo

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}

object Main extends MainTrait{}