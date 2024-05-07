package it.gov.pagopa.tests

import akka.actor.ActorSystem
import it.gov.pagopa.MainTrait
import it.gov.pagopa.common.message.ReRequest
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.AppLogger
import it.gov.pagopa.common.util.ConfigUtil.ConfigData
import it.gov.pagopa.common.util.azure.cosmos.CosmosBuilder
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, when}

import scala.concurrent.{ExecutionContext, Future}

object MainTestMain extends MainTrait {

  override val cosmosRepository = mock[CosmosRepository]
  override val storageBuilder: CosmosBuilder = mock[CosmosBuilder]
  when(storageBuilder.build()(any[ExecutionContext], any[ActorSystem], any[AppLogger])).thenReturn((request: ReRequest, log: AppLogger, data: ConfigData) => {
    Future.successful(())
  })
}
