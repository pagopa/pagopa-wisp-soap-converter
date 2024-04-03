import com.typesafe.sbt.packager.Keys.{dockerBaseImage, dockerExposedPorts, dockerUpdateLatest, packageName}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.{Resolver, ThisBuild, url}
import sbtrelease.ReleaseStateTransformations._
import sbtsonar.SonarPlugin.autoImport.sonarProperties

import java.nio.file.{Files, Paths}

lazy val sonarSettings = Seq(
  sonarProperties ++= Map(
    "sonar.host.url" -> "https://sonarcloud.io/",
    "sonar.projectName" -> "pagopa-wisp-soap-converter",
    "sonar.projectKey" -> "pagopa_pagopa-wisp-soap-converter",
    "sonar.organization" -> "pagopa",
  )
)

logLevel := Level.Debug

ThisBuild / organization := "it.gov.pagopa"
ThisBuild / scalaVersion := "2.13.6"
ThisBuild / version := sys.props.getOrElse("buildNumber", "dev-SNAPSHOT")
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / resolvers += resolverName at resolverAt
ThisBuild / resolvers += Resolver.url(resolverIvyName, url(resolverIvyURL))(
  Resolver.ivyStylePatterns
)

lazy val akka = "2.6.16"
lazy val akkaTool = "1.1.1"
lazy val akkaHttp = "10.2.6"
lazy val logstash = "6.3"
lazy val logback = "1.2.3"
lazy val mssqlJdbc = "7.0.0.jre8"
lazy val scalaXmlVersion = "1.3.0"
lazy val scalaParserCombinators = "1.1.2"
lazy val scalatest = "3.1.0"
lazy val scalamock = "5.1.0"
lazy val dispatch = "0.12.0"
lazy val bcpkixJdk15On = "1.69"
lazy val spotify = "8.16.0"
lazy val chill = "0.9.3"
lazy val scalaLogging = "3.9.2"
lazy val log4jOverSlf4j = "1.7.29"
lazy val commonsEmail = "1.5"
lazy val commonsIo = "2.6"
lazy val guava = "19.0"
lazy val freemarker = "2.3.29"
lazy val itext = "2.1.7"
lazy val opencsv = "2.3"
lazy val jaxbImpl = "2.1.13"
lazy val yamusca = "0.7.0"
lazy val scalai18n = "1.0.3"
lazy val playjson = "2.7.4"
lazy val scalazcore = "7.2.27"
lazy val jackson = "2.14.1"
lazy val galimatias = "0.2.1"
lazy val scalaj = "2.4.2"
lazy val jaxbapi = "2.3.0"
lazy val micrometerRegistryPrometheus = "1.8.1"
lazy val micrometerJvmExtra = "0.2.2"
lazy val zerohash = "0.15"
lazy val azureStorageBlob = "12.22.2"
lazy val azureStorageTable = "12.3.19"
lazy val azureCosmos = "4.56.0"
lazy val azureIdentity = "1.9.0"

lazy val applicationinsightsagentName = "applicationinsights-agent"
lazy val applicationinsightsagentVersion = "3.4.10"
val lightbendKey = sys.env.getOrElse("LIGHTBEND_KEY","or3B1auQImlZDkYZz72Yk9XJ-iT8SIDBwsTEriVrqeymHNLc")
val isJenkinsBuild  = sys.env.getOrElse("JENKINS_BUILD", "false").equalsIgnoreCase("true")

val resolverName = if(isJenkinsBuild) { "Artifactory"} else {"lightbend-commercial-mvn"}
val resolverAt = if(isJenkinsBuild) { "https://toolbox.sia.eu/artifactory/sbt-pagopa/"} else {s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases"}
val resolverIvyName = if(isJenkinsBuild) { "Artifactory-ivy" } else { "lightbend-commercial-ivy" }
val resolverIvyURL = if (isJenkinsBuild) { "https://toolbox.sia.eu/artifactory/sbt-pagopa" } else {s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases"}


lazy val commonSettings: Seq[Def.Setting[_]] = Seq(
  updateOptions := updateOptions.value.withGigahorse(false),
  scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8"),
  classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars,
  Compile / run / fork := true,
  Test / fork := true,
  (Compile / packageBin) / publishArtifact := true,
  (Compile / packageDoc) / publishArtifact := false,
  (Compile / packageSrc) / publishArtifact := false
)

lazy val `wisp-soap-converter`: Project = (project in file("."))
  .aggregate(`common-xml`, `wispsoapconverter`)
  .settings(
    commonSettings,
    publish / skip := true,
    update / aggregate := false,
    homepage := Some(url("https://github.com/pagopa/pagopa-wisp-soap-converter")),
    scmInfo := homepage.value.map(url => ScmInfo(url, "scm:git@github.com:pagopa/pagopa-wisp-soap-converter.git")),
    commands += Command.command("prepare-release")((state: State) => {
      println("Preparing release...")
      val extracted = Project extract state
      val st = extracted.appendWithSession(
        Seq(
          releaseProcess := Seq[ReleaseStep](
            checkSnapshotDependencies, // : ReleaseStep
            inquireVersions, // : ReleaseStep
            runClean, // : ReleaseStep
            runTest, // : ReleaseStep
            setReleaseVersion, // : ReleaseStep
            commitReleaseVersion // : ReleaseStep, performs the initial git checks
            //          tagRelease,                             // : ReleaseStep
            //          publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
            //          setNextVersion,                         // : ReleaseStep
            //          commitNextVersion,                      // : ReleaseStep
            //          pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
          )
        ),
        state
      )
      Command.process("release with-defaults", st)
    }),
    commands += Command.command("complete-release")((state: State) => {
      println("Preparing release...")
      val extracted = Project extract state
      var st = extracted.appendWithSession(
        Seq(
          releaseProcess := Seq[ReleaseStep](
            //          checkSnapshotDependencies,              // : ReleaseStep
            inquireVersions, // : ReleaseStep
            //          runClean,                               // : ReleaseStep
            //          runTest,                                // : ReleaseStep
            //          setReleaseVersion,                      // : ReleaseStep
            //          commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
            //          tagRelease,                             // : ReleaseStep
            //          publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
            setNextVersion, // : ReleaseStep
            commitNextVersion, // : ReleaseStep
            pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
          )
        ),
        state
      )
      Command.process("release with-defaults", st)
    })
  )

val `gen-xml-enum`: sbt.TaskKey[Unit] = taskKey[Unit]("gen-xml-enum")

lazy val `common-xml` = (project in file("common-xml"))
  .enablePlugins(ScalaxbPlugin)
  .settings(
    commonSettings,
    Scalaxb.scalaxbSettings,
    libraryDependencies ++= {
      Seq("javax.xml.bind" % "jaxb-api" % jaxbapi, "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion, "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParserCombinators)
    },
    `gen-xml-enum` := {
      println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@")
      println(s"########## generating xml classes ##########")
      sys.env.foreach { case (key, value) =>
        println(s"$key -> $value")
      }

      val srcMain = (Compile / baseDirectory).value / "src/main"
      val file = (Compile / target).value / "wsdl-schema"
      val srcMainPath = srcMain.getPath
      val schemap = file.getPath

      val schemaPath = Paths.get(schemap)
      if (Files.exists(schemaPath)) {
        new scala.reflect.io.Directory(new File(schemap)).deleteRecursively()
      }
      println(s"Create dirs: $schemaPath")
      Files.createDirectories(schemaPath)

      val toCopy = Map(
        "deprecated/general/sac-common-types-1.0.xsd" -> Seq("paginf"),
        "deprecated/general/PagInf_RPT_RT_6_2_0.xsd" -> Seq("paginf"),
        "deprecated/nodo/NodoPerPa.wsdl" -> Seq("nodoperpa"),
        "deprecated/general/envelope.xsd" -> Seq("nodoperpa")
      )

      toCopy.foreach(tc => {
        tc._2.foreach(dest => {
          val destPath = Paths.get(s"$schemap/$dest")
          if (!Files.exists(destPath)) {
            println(s"creating dir ${destPath}")
            Files.createDirectory(destPath)
          } else {
            println(s"exists dir ${destPath}")
          }
          println(s"copying $srcMainPath/${tc._1} to ${destPath / tc._1}")
          Files.copy(Paths.get(s"$srcMainPath/${tc._1}"), destPath / tc._1.replaceAll(".*\\/", ""))
        })
      })

      val sourcemanaged = (Compile / sourceManaged).value
      val resourcemanaged = (Compile / resourceManaged).value
      WsdlBuild.main(Array(sourcemanaged.getPath, resourcemanaged.getPath))

    },
    Compile / compile := ((Compile / compile) dependsOn `gen-xml-enum`).value,
    Compile / sourceGenerators += (Def.task {
      val file = (Compile / sourceManaged).value / "sbt-xsd" / "it" / "gov" / "pagopa" / "commonxml"
      if (file.exists()) {
        file.listFiles().toSeq
      } else Nil
    } dependsOn `gen-xml-enum`).taskValue,
    Compile / resourceGenerators += (Def.task {
      val file = (Compile / resourceManaged).value / "xml-schema"
      if (file.exists()) {
        file.listFiles().flatMap(d => d.listFiles()).toSeq
      } else Nil
    } dependsOn `gen-xml-enum`).taskValue
  )
import com.github.eikek.sbt.openapi.{CustomMapping, Field, Imports, Pkg, ScalaConfig, TypeDef}
lazy val `wispsoapconverter` = (project in file("wispsoapconverter"))
  .dependsOn(`common-xml`)
  .enablePlugins(BuildInfoPlugin,Cinnamon, JavaAppPackaging, DockerPlugin, JavaAgent, OpenApiSchema)
  .settings(sonarSettings)
  .settings(
    coverageExcludedPackages := ".*tests.*;.*commonxml.*;it\\.gov\\.pagopa\\.soapinput\\.message.*;.*exception.*;.*it.gov.pagopa.common.repo.*",
    coverageExcludedFiles := ".*commonxml.*;.*message.*;.*exception.*;.*StorageBuilder;.*DeadLetterMonitorActor;.*XsdResourceResolver",
//    jacocoReportSettings := JacocoReportSettings(title = "WISP SOAP Converter", formats = Seq(JacocoReportFormats.XML)),
//    jacocoExcludes := Seq("scalaxbmodel.nodoperpsp.*",
//      "scalaxbmodel.nodeforpsp.*",
//      "scalaxbmodel.papernodopagamentopsp.*",
//      "it.gov.pagopa.commonxml.*",
//      "it.gov.pagopa.soapinput.message.*",
//      "it.gov.pagopa.exception.*",
//
//    ),
    javaAgents += "com.microsoft.azure" % applicationinsightsagentName % applicationinsightsagentVersion,
    bashScriptExtraDefines := bashScriptExtraDefines.value.filterNot(_.contains("applicationinsights-agent")) :+
      s"""
         |if [[ "$$AZURE_INSIGHTS_ENABLED" = "true" ]]; then
         |  addJava "-javaagent:$${app_home}/../$applicationinsightsagentName/$applicationinsightsagentName-$applicationinsightsagentVersion.jar"
         |fi
         |""".stripMargin,
    openapiTargetLanguage := Language.Scala,
    openapiPackage := Pkg("it.gov.pagopa.config"),
    openapiScalaConfig := ScalaConfig()
      .addMapping(CustomMapping.forType({
        case TypeDef("LocalDateTime", _) =>
          TypeDef("OffsetDateTime", Imports("java.time.OffsetDateTime"))
      })).addMapping(CustomMapping.forType({
        case TypeDef("Protocol", _) =>
          TypeDef("String", Imports("java.lang.String"))
      })).addMapping(CustomMapping.forField({
        case Field(prop, annot, typeDef) if prop.name == "type" =>
          Field(prop.copy(name = prop.name.mkString("`", "", "`")), annot, typeDef)
        case Field(prop, annot, typeDef) if prop.name.contains("_") =>
          val first = prop.name.substring(0,prop.name.indexOf("_"))
          var tail = prop.name.substring(prop.name.indexOf("_")).split("_")
          var newname = s"$first${tail.map(_.capitalize).mkString("")}"
          val newname2 = s"""@com.fasterxml.jackson.annotation.JsonProperty("${prop.name}") ${newname}"""
          Field(prop.copy(name = newname2), annot , typeDef)
      })),
    openapiSpec :=(Compile / resourceDirectory).value / "openapi_config.json",
    commonSettings,
    libraryDependencies += Cinnamon.library.cinnamonSlf4jMdc,
    libraryDependencies += Cinnamon.library.cinnamonAkka,
    libraryDependencies += Cinnamon.library.cinnamonPrometheus,
    libraryDependencies += Cinnamon.library.cinnamonPrometheusHttpServer,
    libraryDependencies += Cinnamon.library.cinnamonJvmMetricsProducer,
    libraryDependencies += Cinnamon.library.cinnamonAkkaHttp,
    libraryDependencies += Cinnamon.library.cinnamonJmxImporter,
    Test / javaOptions += s"-Duser.dir=${(ThisBuild / baseDirectory).value}/wispsoapconverter/..",
    run / cinnamon := true,
    test / cinnamon := true,
    cinnamonLogLevel := "INFO",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      BuildInfoKey.action("buildTime") {
        System.currentTimeMillis
      }
    ),
    buildInfoPackage := "it.gov.pagopa",
    buildInfoOptions += BuildInfoOption.ToJson,
    Compile / mainClass := Some("it.gov.pagopa.Main"),
    Docker / packageName := "wisp-soap-converter",
    dockerBaseImage := "eclipse-temurin:11.0.22_7-jre-alpine",
    dockerExposedPorts := Seq(8080, 8558, 2552),
    dockerUpdateLatest := true,
    dockerRepository := sys.props.get("docker.registry"),
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN","apk","add","--no-cache", "bash"),
      Cmd("USER", "demiourgos728")
    ),
    Compile / resourceDirectories += baseDirectory.value / "fe" / "build",
    libraryDependencies ++= {
      Seq(
        "net.openhft" % "zero-allocation-hashing" % zerohash,
        "com.typesafe.akka" %% "akka-actor" % akka,
        "com.typesafe.akka" %% "akka-stream" % akka,
        "com.typesafe.akka" %% "akka-slf4j" % akka,
        "com.typesafe.akka" %% "akka-http" % akkaHttp,
        "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttp,
        "com.lightbend.akka.management" %% "akka-management" % akkaTool,
        "io.micrometer" % "micrometer-registry-prometheus" % micrometerRegistryPrometheus,
        "io.github.mweirauch" % "micrometer-jvm-extras" % micrometerJvmExtra,
        "com.github.eikek" %% "yamusca-core" % yamusca,
        "net.logstash.logback" % "logstash-logback-encoder" % logstash,
        "ch.qos.logback" % "logback-classic" % logback,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLogging,
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4j,
        "org.bouncycastle" % "bcpkix-jdk15on" % bcpkixJdk15On,
        "org.bouncycastle" % "bcprov-jdk15on" % bcpkixJdk15On,
        "org.bouncycastle" % "bcutil-jdk15on" % bcpkixJdk15On,
        "com.osinka.i18n" %% "scala-i18n" % scalai18n,
        "com.typesafe.play" %% "play-json" % playjson,
        "com.google.guava" % "guava" % guava,
        "org.scalaz" %% "scalaz-core" % scalazcore,
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % jackson,
        "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jackson,
        "io.mola.galimatias" % "galimatias" % galimatias,
        "com.azure" % "azure-storage-blob" % azureStorageBlob,
        "com.azure" % "azure-data-tables" % azureStorageTable,
        "com.azure" % "azure-cosmos" % azureCosmos,
        "com.azure" % "azure-identity" % azureIdentity,
        "com.typesafe.akka" %% "akka-testkit" % akka % Test,
        "org.scalatest" %% "scalatest" % scalatest % Test,
        "org.scalaj" %% "scalaj-http" % scalaj % Test,
        "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion % Test,
        "org.mockito" %% s"mockito-scala" % "1.17.30" % Test,
        "com.softwaremill.sttp.client4" %% "core" % "4.0.0-M11" % Test,
        "org.mock-server" % s"mockserver-netty" % "5.14.0" % Test
      )
    }
  )
