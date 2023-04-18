package eu.sia.pagopa.mains

import eu.sia.pagopa.testutil.EnvHacker

import java.io.File

trait MainTestUtil {

  def host: String

  println(s"host: $host")
  val bindHost = host //solo per i container mettere "0.0.0.0"
  println(s"bindHost: $bindHost")

  val remotingPort = "2552"
  println(s"remotingPort: $remotingPort")
  val managementPort = "8588"
  println(s"managementPort: $managementPort")
  val prometheusPort = "9091"
  println(s"prometheusPort: $prometheusPort")
  val servicePort = "8088"
  println(s"servicePort: $servicePort")

  EnvHacker.setEnv(
    Map(
      "AKKA_SYSTEM_NAME" -> "fdr-dev",
      "REQUIRED_CONTACT_POINT_NR" -> "1",
      "REMOTING_HOST" -> host,
      "REMOTING_PORT" -> remotingPort,
      "REMOTING_BIND_HOST" -> bindHost,
      "REMOTING_BIND_PORT" -> remotingPort,
      "MANAGEMENT_HTTP_HOST" -> host,
      "MANAGEMENT_HTTP_PORT" -> managementPort,
      "MICROMETER_HOST" -> bindHost,
      "MANAGEMENT_HTTP_BIND_HOST" -> bindHost,
      "MANAGEMENT_HTTP_BIND_PORT" -> managementPort,
      "SERVICE_HTTP_HOST" -> host,
      "SERVICE_HTTP_PORT" -> servicePort,
      "SERVICE_HTTP_BIND_HOST" -> bindHost,
      "SERVICE_HTTP_BIND_PORT" -> servicePort,
      "DB_FDR_USER" -> "fdr",
      "DB_FDR_PASSWORD" -> "password",
      "TZ" -> "Europe/Rome",
      "PROMETHEUS_HOST" -> host,
      "PROMETHEUS_PORT" -> prometheusPort
    )
  )

  val baseConfig = s"${new File(".").getCanonicalPath}/localresources"

  Map(
    "app.bundle.cacerts.path" -> s"$baseConfig/cacerts",
    "logback.configurationFile" -> s"$baseConfig/logback.xml",
    "config.app" -> sys.props.getOrElse("config.app", s"$baseConfig/config-app.conf"),
    "user.language" -> "it",
    "user.country" -> "IT",
    "user.timezone" -> "Europe/Rome",
    "file.encoding" -> "UTF-8"
  ).foreach(f => {
    System.setProperty(f._1, f._2)
    println(s"PROPERTY[-D${f._1}=${f._2}]")
  })
}
