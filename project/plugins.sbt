scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

val lightbendKey = sys.env("LIGHTBEND_KEY")

ThisBuild / resolvers += "lightbend-commercial-mvn" at s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases"
ThisBuild / resolvers += Resolver.url(
  "lightbend-commercial-ivy",
  url(s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases")
)(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n"           % "sbt-buildinfo"         % "0.12.0")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon"          % "2.17.2")
addSbtPlugin("org.scalameta"          % "sbt-scalafmt"          % "2.4.3")
addSbtPlugin("com.github.sbt"         % "sbt-release"           % "1.1.0")
addSbtPlugin("org.scalaxb"            % "sbt-scalaxb"           % "1.8.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("com.github.eikek" % "sbt-openapi-schema" % "0.9.0")

addSbtPlugin("com.sonar-scala" % "sbt-sonar" % "2.3.0")