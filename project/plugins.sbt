addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.2.21")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.29")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.1.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.6.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.9.2")

libraryDependencies += "com.google.cloud" % "google-cloud-storage" % "1.118.0"
