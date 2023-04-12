import sbt.Keys._
import sbt.{ inConfig, inTask, Compile, Def, File }
import sbtscalaxb.ScalaxbKeys._
import sbtscalaxb.ScalaxbPlugin.baseScalaxbSettings

object Scalaxb {
  lazy val wsdls =
    "nodoperpa" ::
    "aim128" ::
    "barcode" ::
    "paginf" ::
    "qrcode" ::
    "flussoriversamento" ::
    "nodeforpsp" ::
    "nodeforpa" ::
    "nodoperpsp" ::
    Nil

  def scalaxbSettings: Seq[Def.Setting[_]] = {
    val namedConfig = wsdls.map(wsdl => {
      val Cfg = sbt.config(wsdl) extend Compile
      (wsdl, Cfg)
    })

    namedConfig.flatMap { case (wsdl, cfg) =>
      val x = inConfig(cfg)(customScalaxbSettings(wsdl))
      println(s"customScalaxbSettings ${wsdl} ")
      x
    } ++ namedConfig.map { case (_, cfg) =>
      Compile / sourceGenerators += (cfg / scalaxb).taskValue
    }
  }

  def customScalaxbSettings(base: String): Seq[Def.Setting[_]] = {

    baseScalaxbSettings ++ inTask(scalaxb)(
      Seq(
        sourceManaged := (Compile / sourceManaged).value,
        scalaxbGenerateRuntime := (base == "nodeforpsp"),
        scalaxbGenerateDispatchClient := false,
        scalaxbPackageName := s"scalaxbmodel.${base.toLowerCase}",
        scalaxbProtocolFileName := s"${base}_xmlprotocol.scala",
        scalaxbXsdSource := new File(s"common-xml/target/wsdl-schema/${base.toLowerCase}/"),
        scalaxbWsdlSource := new File(s"common-xml/target/wsdl-schema/${base.toLowerCase}/")
      )
    )
  }
}
