scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

val lightbendKey = sys.env.getOrElse("LIGHTBEND_KEY","5IDMAq0poMpRYz1HD58Y7c8jQ9kjlFs_yKCMkg3tdeBTeqiL")

ThisBuild / resolvers += "lightbend-commercial-mvn" at s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases"
ThisBuild / resolvers += Resolver.url(
  "lightbend-commercial-ivy",
  url(s"https://repo.lightbend.com/pass/${lightbendKey}/commercial-releases")
)(Resolver.ivyStylePatterns)

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon"          % "2.16.2")
addSbtPlugin("org.scalameta"          % "sbt-scalafmt"          % "2.4.3")
addSbtPlugin("com.github.sbt"         % "sbt-release"           % "1.1.0")
addSbtPlugin("net.virtual-void"       % "sbt-dependency-graph"  % "0.10.0-RC1")
addSbtPlugin("org.scalaxb"            % "sbt-scalaxb"           % "1.8.2")
addSbtPlugin("com.github.sbt"         % "sbt-jacoco"            % "3.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("com.github.eikek" % "sbt-openapi-schema" % "0.9.0")