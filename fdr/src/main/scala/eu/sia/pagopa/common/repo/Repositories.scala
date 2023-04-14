package eu.sia.pagopa.common.repo

import com.typesafe.config.Config
import eu.sia.pagopa.common.repo.fdr.FdrRepository
import eu.sia.pagopa.common.util.NodoLogger
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

case class Repositories(config: Config, log: NodoLogger)(implicit ec: ExecutionContext) {

  var fdrRepositoryInitialized = false

  lazy val fdrRepository: FdrRepository = {
    log.info(s"Starting repository...")
    val fdr: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("database.fdr", config)
    fdrRepositoryInitialized = true
    FdrRepository(fdr.profile, fdr.db)
  }
}
