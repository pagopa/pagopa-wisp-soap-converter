package it.gov.pagopa.common.repo

import com.azure.cosmos.{CosmosClientBuilder, CosmosException}
import com.azure.cosmos.models.PartitionKey
import com.typesafe.config.Config
import it.gov.pagopa.common.util._
import it.gov.pagopa.common.util.azure.cosmos.RtEntity
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class CosmosRepository(config:Config, log: AppLogger)(implicit ec: ExecutionContext) {

  lazy val cosmosEndpoint = config.getString("azure-cosmos-data.endpoint")
  lazy val cosmosKey = config.getString("azure-cosmos-data.key")
  lazy val cosmosDbName = config.getString("azure-cosmos-data.db-name")
  lazy val cosmosTableName = config.getString("azure-cosmos-data.table-name")
  lazy val client = new CosmosClientBuilder().endpoint(cosmosEndpoint).key(cosmosKey).buildClient

//  def query(query: SqlQuerySpec) = {
//    log.info("executing query:" + query.getQueryText)
//    val container = client.getDatabase(cosmosDbName).getContainer(cosmosTableName)
//    container.queryItems(query, new CosmosQueryRequestOptions, classOf[PositiveBizEvent])
//  }

  def read(key: String, table: String): Future[Option[CosmosPrimitive]] = {
    Future {
      val container = client.getDatabase(cosmosDbName).getContainer(table)
      val itemResponse = container.readItem(key, new PartitionKey(key), classOf[CosmosPrimitive])
      Option(itemResponse.getItem)
    }.recover {
      case ex: Exception =>
        log.error(s"Error reading item with key $key from CosmosDB: ${ex.getMessage}")
        None
    }
  }

  def getRtByKey(key: String): Future[Option[RtEntity]] = {
    Future {
      val container = client.getDatabase(cosmosDbName).getContainer("receipts-rt")
      val response = container.readItem(key, new PartitionKey(key), classOf[String])

      if (response.getStatusCode == 200) {
        val json = response.getItem
        Some(Json.parse(json).as[RtEntity])
      } else {
        None
      }
    }.recover {
      case ex: CosmosException =>
        log.error(s"Failed to read item with key $key: ${ex.getMessage}")
        None
    }
  }

  def save(item:CosmosPrimitive) = {
    Future({
      val s = client.getDatabase(cosmosDbName).getContainer(cosmosTableName)
        .createItem(item)
      s.getStatusCode
    })
  }
}
