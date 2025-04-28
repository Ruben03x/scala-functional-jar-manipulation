ThisBuild / version      := "0.1.0-SNAPSHOT"
name                     := "java-lib-detect"
ThisBuild / scalaVersion := "3.6.1"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt"                         % "4.1.0",
  "com.lihaoyi"      %% "upickle"                       % "4.1.0",
  "org.typelevel"    %% "cats-effect"                   % scalaVersion.value,
  "org.typelevel"    %% "cats-effect-testing-scalatest" % "1.6.0" % Test,
  "org.scalatest"    %% "scalatest"                     % "3.2.19"           % Test
)

resolvers ++= Seq(
  "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/public",
  "Atlassian" at "https://packages.atlassian.com/mvn/maven-atlassian-external",
  "Gradle Releases" at "https://repo.gradle.org/gradle/libs-releases/"
)

enablePlugins(JavaAppPackaging, LauncherJarPlugin)

scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Wunused:imports"
)
