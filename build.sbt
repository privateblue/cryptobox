val catsVersion = "2.2.0"
val catsMtlVersion = "1.0.0"
val http4sVersion = "1.0.0-M5+51-b7bc8ae0-SNAPSHOT"
val circeVersion = "0.13.0"
val starkEcdsaVersion = "1.0.0"
val scalatestVersion = "3.2.2"

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full
)

lazy val root = (project in file("."))
  .settings(
    name := "cryptobox",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-mtl" % catsMtlVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.starkbank.ellipticcurve" % "starkbank-ecdsa" % starkEcdsaVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    ),
    scalaVersion := "2.13.3",
    fork := true
  )
