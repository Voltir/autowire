crossScalaVersions := Seq("2.10.4", "2.11.4")

//use resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
lazy val sonatypeSettings = Seq(
  organization := "com.stabletechs",
  version := "0.2.6-SNAPSHOT",
  name := "autowire",
  scalaVersion := "2.11.7",

  // Sonatype
  publishArtifact in Test := false,

  //Forking autowire until Li Haoyi gets more time to deal with our pull request.
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  sonatypeProfileName := "com.stabletechs",
  pomExtra := (
    <developers>
      <developer>
        <id>Voltaire</id>
        <name>Nick Childers</name>
        <url>https://github.com/voltir/</url>
      </developer>
    </developers>
    ),
  pomIncludeRepository := { _ => false }
)

val autowire = crossProject.settings(sonatypeSettings:_*).settings(
  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided",
    "com.lihaoyi" %%% "utest" % "0.3.1" % "test",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.lihaoyi" %%% "upickle" % "0.2.6" % "test"
  ) ++ (
    if (scalaVersion.value startsWith "2.11.") Nil
    else Seq(
      compilerPlugin("org.scalamacros" % s"paradise" % "2.0.0" cross CrossVersion.full),
      "org.scalamacros" %% s"quasiquotes" % "2.0.0"
    )
    ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  unmanagedSourceDirectories in Compile ++= {
    if (scalaVersion.value startsWith "2.10.") Seq(baseDirectory.value / ".."/"shared"/"src"/ "main" / "scala-2.10")
    else Seq(baseDirectory.value / ".."/"shared" / "src"/"main" / "scala-2.11")
  },
  //Vary compileTimeOnly based on scala version
  unmanagedSourceDirectories in Compile ++= {
    if (scalaVersion.value startsWith "2.10.") Seq(baseDirectory.value / "shared" / "main" / "scala-2.10")
    else Seq(baseDirectory.value /".."/ "shared"/"src"/"main"/ "scala-2.11")
  }
).jsSettings(
    resolvers ++= Seq(
      "bintray-alexander_myltsev" at "http://dl.bintray.com/content/alexander-myltsev/maven"
    ),
    scalaJSStage in Test := FullOptStage
).jvmSettings(
  resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(
    "org.scala-lang" %% "scala-pickling" % "0.9.0" % "test",
    "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % "test",
    "com.typesafe.play" %% "play-json" % "2.3.0" % "test"
  )
)

lazy val root = project.in(file(".")).settings(sonatypeSettings:_*).aggregate(autowireJS, autowireJVM)
lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm
