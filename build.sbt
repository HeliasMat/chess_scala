val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "pure-chess-engine",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      // The Core FP Stack
      "org.typelevel" %% "cats-core"    % "2.10.0",
      "org.typelevel" %% "cats-effect"  % "3.5.2", // IO Monad

      // Streaming IO (for UCI Protocol)
      "co.fs2"        %% "fs2-core"     % "3.9.3",
      "co.fs2"        %% "fs2-io"       % "3.9.3",

      // Optics for Immutable Data Access
      "dev.optics"    %% "monocle-core"  % "3.2.0",
      "dev.optics"    %% "monocle-macro" % "3.2.0",

      // Parsing
      "org.typelevel" %% "cats-parse"    % "1.0.0",

      // Testing
      "org.scalacheck" %% "scalacheck"   % "1.17.0" % Test
    ),

    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-Werror"
    )
  )