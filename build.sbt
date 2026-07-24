name := "littleman"

version := "0.1"

scalaVersion := "2.13.17"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
fork := true

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)