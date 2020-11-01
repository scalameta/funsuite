import com.typesafe.tools.mima.core.DirectMissingMethodProblem
import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core.MissingTypesProblem
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import scala.collection.mutable
val customScalaJSVersion = Option(System.getenv("SCALAJS_VERSION"))
val scalaJSVersion = customScalaJSVersion.getOrElse("1.3.0")
val scalaNativeVersion = "0.4.0-M2"
def previousVersion = "0.7.0"
def scala213 = "2.13.2"
def scala212 = "2.12.11"
def scala211 = "2.11.12"
def dottyNext = "0.27.0-RC1"
def dottyStable = "0.26.0"
def junitVersion = "4.13"
def gcp = "com.google.cloud" % "google-cloud-storage" % "1.113.2"
inThisBuild(
  List(
    version ~= { old =>
      if ("true" == System.getProperty("CI") && old.contains("+0-")) {
        old.replaceAll("\\+0-.*", "")
      } else {
        old
      }
    },
    organization := "org.scalameta",
    homepage := Some(url("https://github.com/scalameta/munit")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "olafurpg",
        "Ólafur Páll Geirsson",
        "olafurpg@gmail.com",
        url("https://geirsson.com")
      )
    ),
    scalaVersion := scala213,
    testFrameworks := List(
      new TestFramework("munit.Framework")
    ),
    useSuperShell := false,
    scalacOptions ++= List(
      "-target:jvm-1.8",
      "-language:implicitConversions"
    )
  )
)

skip in publish := true
mimaPreviousArtifacts := Set.empty
crossScalaVersions := List()
addCommandAlias(
  "scalafixAll",
  "; ++2.12.10 ; scalafixEnable ; all scalafix test:scalafix"
)
addCommandAlias(
  "scalafixCheckAll",
  "; ++2.12.10 ;  scalafixEnable ; scalafix --check ; test:scalafix --check"
)
val isPreScala213 = Set[Option[(Long, Long)]](Some((2, 11)), Some((2, 12)))
val scala2Versions = List(scala213, scala212, scala211)
val scala3Versions = List(dottyNext, dottyStable)
val allScalaVersions = scala2Versions ++ scala3Versions
def isNotScala211(v: Option[(Long, Long)]): Boolean = !v.contains((2, 11))
def isScala2(v: Option[(Long, Long)]): Boolean = v.exists(_._1 == 2)
def isScala3(v: Option[(Long, Long)]): Boolean = v.exists(_._1 == 0)
val isScalaJS = Def.setting[Boolean](
  SettingKey[Boolean]("scalaJSUseMainModuleInitializer").?.value.isDefined
)
val isScalaNative = Def.setting[Boolean](
  SettingKey[String]("nativeGC").?.value.isDefined
)

// NOTE(olafur): disable Scala.js and Native settings for IntelliJ.
lazy val skipIdeaSettings = SettingKey[Boolean]("ide-skip-project") := true
lazy val mimaEnable: List[Def.Setting[_]] = List(
  mimaBinaryIssueFilters ++= List(
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.MUnitRunner.descriptions"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.MUnitRunner.testNames"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.MUnitRunner.munitTests"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.ValueTransforms.munitTimeout"
    ),
    ProblemFilters.exclude[MissingTypesProblem]("munit.FailException"),
    ProblemFilters.exclude[MissingTypesProblem]("munit.FailSuiteException"),
    ProblemFilters.exclude[MissingTypesProblem](
      "munit.TestValues$FlakyFailure"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.internal.junitinterface.JUnitComputer.this"
    )
  ),
  mimaPreviousArtifacts := {
    if (crossPaths.value)
      Set("org.scalameta" %% moduleName.value % previousVersion)
    else Set("org.scalameta" % moduleName.value % previousVersion)
  }
)
val sharedJVMSettings: List[Def.Setting[_]] = List(
  crossScalaVersions := allScalaVersions
) ++ mimaEnable
val sharedJSSettings: List[Def.Setting[_]] = List(
  skipIdeaSettings,
  crossScalaVersions := scala2Versions,
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
)
val sharedJSConfigure: Project => Project =
  _.disablePlugins(MimaPlugin)
val sharedNativeSettings: List[Def.Setting[_]] = List(
  skipIdeaSettings,
  scalaVersion := scala211,
  crossScalaVersions := List(scala211)
)
val sharedNativeConfigure: Project => Project =
  _.disablePlugins(ScalafixPlugin, MimaPlugin)

val sharedSettings = List(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        List(
          "-Yrangepos",
          "-Xexperimental",
          "-Ywarn-unused-import"
        )
      case Some((0, _)) => List()
      case _ =>
        List(
          "-target:jvm-1.8",
          "-Yrangepos",
          // -Xlint is unusable because of
          // https://github.com/scala/bug/issues/10448
          "-Ywarn-unused:imports"
        )
    }
  }
)

lazy val junit = project
  .in(file("junit-interface"))
  .settings(
    mimaEnable,
    moduleName := "junit-interface",
    description := "A Java implementation of sbt's test interface for JUnit 4",
    skip in publish := customScalaJSVersion.isDefined,
    autoScalaLibrary := false,
    crossPaths := false,
    sbtPlugin := false,
    crossScalaVersions := List(allScalaVersions.head),
    libraryDependencies ++= List(
      "junit" % "junit" % junitVersion,
      "org.scala-sbt" % "test-interface" % "1.0"
    ),
    javacOptions in Compile ++= List("-target", "1.8", "-source", "1.8"),
    javacOptions in (Compile, doc) --= List("-target", "1.8")
  )

lazy val munit = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .settings(
    sharedSettings,
    unmanagedSourceDirectories.in(Compile) ++=
      crossBuildingDirectories("munit", "main").value,
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((0, _)) => Nil
        case _ =>
          List(
            "org.scala-lang" % "scala-reflect" % scalaVersion.value
          )
      }
    }
  )
  .nativeConfigure(sharedNativeConfigure)
  .nativeSettings(
    sharedNativeSettings,
    skip in publish := customScalaJSVersion.isDefined,
    libraryDependencies ++= List(
      "org.scala-native" %%% "test-interface" % scalaNativeVersion
    )
  )
  .jsConfigure(sharedJSConfigure)
  .jsSettings(
    sharedJSSettings,
    libraryDependencies ++= List(
      "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion,
      "org.scala-js" %% "scalajs-junit-test-runtime" % scalaJSVersion
    )
  )
  .jvmSettings(
    sharedJVMSettings,
    skip in publish := customScalaJSVersion.isDefined,
    libraryDependencies ++= List(
      "junit" % "junit" % "4.13.1"
    )
  )
  .jvmConfigure(_.dependsOn(junit))
lazy val munitJVM = munit.jvm
lazy val munitJS = munit.js
lazy val munitNative = munit.native

lazy val plugin = project
  .in(file("munit-sbt"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sharedSettings,
    moduleName := "sbt-munit",
    sbtPlugin := true,
    skip in publish := customScalaJSVersion.isDefined,
    scalaVersion := scala212,
    crossScalaVersions := List(scala212),
    buildInfoPackage := "munit.sbtmunit",
    buildInfoKeys := Seq[BuildInfoKey](
      "munitVersion" -> version.value
    ),
    crossScalaVersions := List(scala212),
    libraryDependencies ++= List(
      gcp
    )
  )
  .disablePlugins(MimaPlugin)

lazy val munitScalacheck = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("munit-scalacheck"))
  .dependsOn(munit)
  .settings(
    moduleName := "munit-scalacheck",
    sharedSettings,
    libraryDependencies += "org.scalacheck" %%% "scalacheck" % "1.15.0"
  )
  .jvmSettings(
    sharedJVMSettings,
    skip in publish := customScalaJSVersion.isDefined
  )
  .nativeConfigure(sharedNativeConfigure)
  .nativeSettings(
    sharedNativeSettings,
    skip in publish := customScalaJSVersion.isDefined
  )
  .jsConfigure(sharedJSConfigure)
  .jsSettings(sharedJSSettings)
lazy val munitScalacheckJVM = munitScalacheck.jvm
lazy val munitScalacheckJS = munitScalacheck.js
lazy val munitScalacheckNative = munitScalacheck.native

lazy val tests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .dependsOn(munit, munitScalacheck)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sharedSettings,
    buildInfoPackage := "munit",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceDirectory" ->
        baseDirectory.in(ThisBuild).value / "tests" / "shared" / "src" / "main",
      scalaVersion
    ),
    unmanagedSourceDirectories.in(Test) ++=
      crossBuildingDirectories("tests", "test").value,
    skip in publish := true
  )
  .nativeConfigure(sharedNativeConfigure)
  .nativeSettings(sharedNativeSettings)
  .jsConfigure(sharedJSConfigure)
  .jsSettings(sharedJSSettings)
  .jvmSettings(
    sharedJVMSettings,
    fork := true
  )
  .disablePlugins(MimaPlugin)
lazy val testsJVM = tests.jvm
lazy val testsJS = tests.js
lazy val testsNative = tests.native

lazy val docs = project
  .in(file("munit-docs"))
  .dependsOn(munitJVM, munitScalacheckJVM)
  .enablePlugins(MdocPlugin, MUnitReportPlugin, DocusaurusPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    sharedSettings,
    moduleName := "munit-docs",
    skip in publish := customScalaJSVersion.isDefined,
    crossScalaVersions := List(scala213, scala212),
    unmanagedSources.in(Compile) += sourceDirectory
      .in(plugin, Compile)
      .value / "scala" / "munit" / "sbtmunit" / "MUnitTestReport.scala",
    libraryDependencies ++= List(
      "org.scala-lang.modules" %% "scala-xml" % "2.0.0-M2",
      gcp
    ),
    test := {},
    munitRepository := Some("scalameta/munit"),
    mdocOut :=
      baseDirectory.in(ThisBuild).value / "website" / "target" / "docs",
    mdocExtraArguments := List("--no-link-hygiene"),
    mdocVariables := Map(
      "VERSION" -> version.value.replaceFirst("\\+.*", ""),
      "DOTTY_VERSION" -> dottyNext,
      "DOTTY_NEXT_VERSION" -> dottyNext,
      "DOTTY_STABLE_VERSION" -> dottyStable,
      "SUPPORTED_SCALA_VERSIONS" -> allScalaVersions.mkString(", ")
    ),
    fork := false
  )

def crossBuildingDirectories(name: String, config: String) =
  Def.setting[Seq[File]] {
    val root = baseDirectory.in(ThisBuild).value / name
    val base = root / "shared" / "src" / config
    val result = mutable.ListBuffer.empty[File]
    val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
    if (isScalaJS.value || isScalaNative.value) {
      result += root / "non-jvm" / "src" / config
    }
    if (isPreScala213(partialVersion)) {
      result += base / "scala-pre-2.13"
    }
    if (isNotScala211(partialVersion)) {
      result += base / "scala-post-2.11"
    }
    if (isScala2(partialVersion)) {
      result += base / "scala-2"
    }
    if (isScala3(partialVersion)) {
      result += base / "scala-3"
    }
    result.toList
  }
