package eu.sia.pagopa.common.repo

import com.typesafe.config.Config
import eu.sia.pagopa.common.repo.offline.OfflineRepository
import eu.sia.pagopa.common.util.NodoLogger
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

case class Repositories(config: Config, log: NodoLogger)(implicit ec: ExecutionContext) {

  var offlineRepositoryInitialized = false

  lazy val offlineRepository: OfflineRepository = {
    log.info(s"Starting OfflineRepository...")
    val offline: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("database.offline", config)
    offlineRepositoryInitialized = true
    OfflineRepository(offline.profile, offline.db)
  }
}
