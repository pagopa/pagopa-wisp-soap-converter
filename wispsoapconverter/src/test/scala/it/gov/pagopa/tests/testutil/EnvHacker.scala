package it.gov.pagopa.tests.testutil

import java.io.File

trait EnvHacker {

  //  /**
  //   * Portable method for setting env vars on both *nix and Windows.
  //   * @see http://stackoverflow.com/a/7201825/293064
  //   */
  //  def envFile: String = "/127.0.0.1.env"

  def setEnv(values: Map[String, String]): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    values.foreach(x => map.put(x._1, x._2))
    printEnv(values)
  }

  def setFileEnv(envFile: File): Unit = {
    println(s"From file $envFile")
    import scala.io.Source

    val envPropertyPattern = """^([^=]+)=(?:\")?(.*?)(?:\")?$""".r
    val mapp: Map[String, String] = Source
      .fromFile(envFile)
      .getLines()
      .map(_.trim)
      .flatMap {
        case envPropertyPattern(key, value) =>
          Some(key -> value)
        case _ =>
          None
      }
      .toMap
    setEnv(mapp)
  }

  def printEnv(m: Map[String, String]): Unit = m.toList.sortBy(a => a).foreach(l => println(s"ENV[${l._1}=${l._2}]"))
}

object EnvHacker extends EnvHacker
