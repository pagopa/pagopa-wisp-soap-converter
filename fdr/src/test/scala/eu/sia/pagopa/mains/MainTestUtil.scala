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
  val managementPort = "8558"
  println(s"managementPort: $managementPort")
  val prometheusPort = "9091"
  println(s"prometheusPort: $prometheusPort")
  val servicePort = "8080"
  println(s"servicePort: $servicePort")

  EnvHacker.setEnv(
    Map(
      "AKKA_SYSTEM_NAME" -> "nodo-dev",
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
      "DB_CONFIG_USER" -> "NODO4_CFG_DEV",
      "DB_CONFIG_PASSWORD" -> "NODO4_CFG_DEV",
      "DB_ONLINE_USER" -> "NODO_ONLINE_DEV",
      "DB_ONLINE_PASSWORD" -> "NODO_ONLINE_DEV",
      "DB_OFFLINE_USER" -> "NODO_OFFLINE_DEV",
      "DB_OFFLINE_PASSWORD" -> "NODO_OFFLINE_DEV",
      "DB_WFESP_USER" -> "WFESP_DEV",
      "DB_WFESP_PASSWORD" -> "WFESP_DEV",
      "DB_RE_USER" -> "RE_DEV",
      "DB_RE_PASSWORD" -> "RE_DEV",
      "TZ" -> "Europe/Rome",
      "PROMETHEUS_HOST" -> host,
      "PROMETHEUS_PORT" -> prometheusPort,
      "GEC_FEES_SUBSCRIPTION_KEY" -> "6e508a628317485ea1241e57cde7602d"
    )
  )

  val baseConfig = s"${new File(".").getCanonicalPath}/localresources"

  Map(
    "app.bundle.casogei.path" -> s"$baseConfig/CASogeiTest.pem",
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
