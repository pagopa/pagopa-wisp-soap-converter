package eu.sia.pagopa.testutil

import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import liquibase.{Contexts, Liquibase}
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

object DBUtils {

  def initDB(schema: String): JdbcBackend.DatabaseDef = {
    val path = System.getProperty("user.dir")
    val db = Database.forURL(
      s"jdbc:h2:$path/target/$schema;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS $schema\\;SET SCHEMA $schema;",
      driver = "org.h2.Driver",
      executor = AsyncExecutor("test1", minThreads = 10, queueSize = 1000, maxConnections = 10, maxThreads = 10)
    )
    val database =
      DatabaseFactory.getInstance.findCorrectDatabaseImplementation(new JdbcConnection(db.source.createConnection()))
    val resourceAccessorOnline = new FileSystemResourceAccessor(getClass.getResource(s"/changelog/$schema").getPath)
    val liqui = new Liquibase("./db.changelog-master.xml", resourceAccessorOnline, database)
    liqui.update(new Contexts())
    liqui.close()
    db
  }

}
