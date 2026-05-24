ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "com.playmock"

name := "play-mock"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
)
