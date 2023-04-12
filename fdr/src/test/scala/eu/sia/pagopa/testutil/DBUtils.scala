package eu.sia.pagopa.testutil

import com.google.common.io.PatternFilenameFilter
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import liquibase.{Contexts, Liquibase}
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

import java.io.File

object DBUtils {

  private def fixXmlContentForH2(filename: String, schema: String, content: String): String = {
    val c1 = content
      .replaceAll("""\$\{schema\}""", schema)
      .replaceAll("""\$\{schemaOnline\}""", "NODO_ONLINE")
      .replaceAll("""<changeSet author="liquibase" id="000000000001">[\s\S]*?</changeSet>""", "")
      .replaceAll("""forIndexName=".*?"""", "")
      .replaceAll("""utl_raw.cast_to_raw\(' '\)""", "' '")
      .replaceAll("""NUMBER\(\*,""", "NUMBER(22,")
      .replaceAll("""<changeSet author="liquibase" id="202110201804-2">[\s\S]*?</changeSet>""", "")
      .replaceAll("""<changeSet author="liquibase" id="202110201804-3">[\s\S]*?</changeSet>""", "")
      .replaceAll("""<changeSet author="liquibase" id="202110201804-4">[\s\S]*?</changeSet>""", "")
      .replaceAll("""<changeSet author="liquibase" id="202110201804-5">[\s\S]*?</changeSet>""", "")
      .replaceAll("""<changeSet author="liquibase" id="202110201734">[\s\S]*?</changeSet>""", "")
      .replaceAll("""9999999999999999999999999999""", "9223372036854775807")
      .replaceAll("""<modifySql labels="ignore">[\s\S]*</modifySql>""", "")
      .replaceAll("""db.changelog-0.xml""", "db.changelog-0-h2.xml")
      .replaceAll("""db.changelog-20221205102000_replace_elenco_servizi_view_canali_nodo.xml""", "db.changelog-20221205102000_replace_elenco_servizi_view_canali_nodo-h2.xml")
      .replaceAll("""db.changelog-20230120114500_restore_dropped_column.xml""", "")
      .replaceAll("""db.changelog-20230124172900_fix_column_type.xml""", "")
      .replaceAll("""db.changelog-20230124172900_set_default_values.xml""", "")
    if (filename == "db.changelog-master.xml") {
      c1.replaceAll("""</databaseChangeLog>""", "<include file=\"./db.changelog-dev-data.xml\"/></databaseChangeLog>")
    } else {
      c1
    }
  }
  def initDB(schema: String, folder: String, additionalContexts: Seq[String] = Seq()): JdbcBackend.DatabaseDef = {
    val path = System.getProperty("user.dir")
    val scriptpath = s"$path/devops/db/liquibase/changelog/$folder/"
    val scriptpathh2 = s"$path/target/db/liquibase/changelog/$folder-h2/"
    val scriptFolder = new File(scriptpath)
    val changelogMaster = s"./db.changelog-master.xml"

    val h2FolderPath = new File(scriptpathh2)
    h2FolderPath.mkdirs()
    scriptFolder
      .listFiles(new PatternFilenameFilter(".*xml"))
      .foreach(f => {
        val content = scala.reflect.io.File(scala.reflect.io.Path.jfile2path(f)).slurp()
        val newcontent = fixXmlContentForH2(f.getName, schema, content)
        val destfile = new File(scriptpathh2 + "/" + f.getName)
        java.nio.file.Files.write(java.nio.file.Path.of(destfile.getPath), newcontent.getBytes)
      })

    val h2resource = new File(getClass.getResource(s"/changelog/${folder}").getPath)

    h2resource
      .listFiles(new PatternFilenameFilter(".*xml"))
      .foreach(f => {

        val content = scala.reflect.io.File(scala.reflect.io.Path.jfile2path(f)).slurp()
        val newcontent = fixXmlContentForH2(f.getName, schema, content)
        val destfile = new File(scriptpathh2 + "/" + f.getName)
        java.nio.file.Files.write(java.nio.file.Path.of(destfile.getPath), newcontent.getBytes)
      })

    //    System.exit(0)
    val db = Database.forURL(
      s"jdbc:h2:$path/target/NODO4;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS $schema\\;SET SCHEMA $schema;",
      driver = "org.h2.Driver",
      executor = AsyncExecutor("test1", minThreads = 10, queueSize = 1000, maxConnections = 10, maxThreads = 10)
    )
    val database =
      DatabaseFactory.getInstance.findCorrectDatabaseImplementation(new JdbcConnection(db.source.createConnection()))
    val resourceAccessorOnline = new FileSystemResourceAccessor(h2FolderPath)
    val liqui = new Liquibase(changelogMaster, resourceAccessorOnline, database)
    liqui.update(new Contexts())
    liqui.close()

    h2FolderPath.delete()

    db
  }

}
