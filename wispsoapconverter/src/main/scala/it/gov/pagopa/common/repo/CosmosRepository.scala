package it.gov.pagopa.common.repo

import com.azure.cosmos.CosmosClientBuilder
import com.typesafe.config.Config
import it.gov.pagopa.common.util._

import scala.concurrent.{ExecutionContext, Future}

class CosmosRepository(config:Config, log: AppLogger)(implicit ec: ExecutionContext) {

  lazy val cosmosEndpoint = config.getString("azure-cosmos.endpoint")
  lazy val cosmosKey = config.getString("azure-cosmos.key")
  lazy val cosmosDbName = config.getString("azure-cosmos.db-name")
  lazy val cosmosTableName = config.getString("azure-cosmos.table-name")
  lazy val client = new CosmosClientBuilder().endpoint(cosmosEndpoint).key(cosmosKey).buildClient

//  def query(query: SqlQuerySpec) = {
//    log.info("executing query:" + query.getQueryText)
//    val container = client.getDatabase(cosmosDbName).getContainer(cosmosTableName)
//    container.queryItems(query, new CosmosQueryRequestOptions, classOf[PositiveBizEvent])
//  }

  def save(item:CosmosPrimitive) = {
    Future({
      val s = client.getDatabase(cosmosDbName).getContainer(cosmosTableName)
        .createItem(item)
      s.getStatusCode
    })
  }
}
