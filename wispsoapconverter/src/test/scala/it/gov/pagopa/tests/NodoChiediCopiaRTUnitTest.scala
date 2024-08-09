package it.gov.pagopa.tests

import akka.actor.{ActorRef, Props}
import it.gov.pagopa.ActorProps
import it.gov.pagopa.actors.NodoChiediCopiaRTActorPerRequest
import it.gov.pagopa.common.message.{ReExtra, SoapRequest}
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.Util
import it.gov.pagopa.common.util.azure.cosmos.RtEntity
import it.gov.pagopa.tests.testutil.TestItems
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockitoSugar

import java.util.UUID
import scala.concurrent.Future


class NodoChiediCopiaRTActorPerRequestTest extends BaseUnitTest with MockitoSugar {

  "NodoChiediCopiaRTActorPerRequest" should {

    "handle a valid SOAP request and return the RT" in {
      // Arrange
      val cosmosRepositoryMock = mock[CosmosRepository]
      val actorPropsMock = mock[ActorProps]

      // Mock
      val mockRtEntity = RtEntity("id", "partitionKey", "idDominio", "iuv", "ccp", Some("OK"), Some("H4sIAAAAAAAAAysqAQCuvLgLAgAAAA=="), Some(123456789L))
      when(cosmosRepositoryMock.getRtByKey(anyString())).thenReturn(Future.successful(Some(mockRtEntity)))

      // Execute
      val nodoChiediCopiaRTActor: ActorRef = system.actorOf(Props(new NodoChiediCopiaRTActorPerRequest(cosmosRepositoryMock, actorPropsMock)))
      val res = askActor(
        nodoChiediCopiaRTActor,
        SoapRequest(UUID.randomUUID().toString, genericPayload("nodoChiediCopiaRT", "iuv", "ccp", Util.now()), TestItems.testPDD, "nodoChiediCopiaRT", "test", Util.now(), ReExtra(), false, None)
      )

      // Assert
      assert(res.payload.nonEmpty)
      assert(!res.payload.contains("faultCode"))
    }
  }
}

