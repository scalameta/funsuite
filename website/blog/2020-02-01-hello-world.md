---
author: Ólafur Páll Geirsson
title: MUnit is a new Scala testing library
authorURL: https://twitter.com/olafurpg
authorImageURL: https://github.com/olafurpg.png
---

Hello world! I'm excited to announce the first release of MUnit, a new Scala
testing library with a focus on actionable errors and extensible APIs. You may
be thinking "Why create Yet Another Scala testing library?". It's a good
question and this post is my attempt to explain the motivations for creating
MUnit.

<!-- truncate -->

Like many other existing testing libraries, MUnit has no external Scala
dependencies and is published for a wide range of compiler versions and
platforms.

| Scala Version  | JVM | Scala.js (0.6.x, 1.x) | Native (0.4.x) |
| -------------- | :-: | :-------------------: | :------------: |
| 2.11.x         | ✅  |          ✅           |       ✅       |
| 2.12.x         | ✅  |          ✅           |      n/a       |
| 2.13.x         | ✅  |          ✅           |      n/a       |
| 0.21.x (Dotty) | ✅  |          n/a          |      n/a       |

MUnit tries to distinguish itself by focusing on the following features:

- **Tests as values**: test cases are represented as normal data structures that
  you can manipulate and abstract over.
- **Rich filtering capabilities**: MUnit provides fine-grained control over what
  tests are enabled for which environments.
- **Actionable errors**: the formatting of failed test cases is optimized for
  giving you as much information as possible to understand how to fix the test
  case.
- **Tooling integrations**: MUnit is implemented as a JUnit runner and tries to
  build on top of existing JUnit functionality where possible.
- **Insightful test reports**: the MUnit sbt plugin allows you to analyze
  historical data about your tests to answer questions like "is this test suite
  flaky?" and "which tests are slowing down my CI?".

## TL;DR

To use MUnit, first add a dependency in your build.

![Badge with version of the latest release](https://img.shields.io/maven-central/v/org.scalameta/munit_2.13?style=for-the-badge)

```scala
// build.sbt
libraryDependencies += "org.scalameta" %% "munit" % "0.4.3"
testFrameworks += new TestFramework("munit.Framework")
```

Next, write a test case:

```scala
// src/test/scala/com/MySuite.scala
class MySuite extends munit.FunSuite {
  test("hello") {
    assert(41 == 42)
  }
}
```

Check out the
[getting started guide](https://scalameta.org/munit/docs/getting-started.html).

## Tests as values

If you know how to write normal Scala programs you should feel comfortable
reasoning about how MUnit works.

Internally, a core MUnit data structure is `Test`, which represents a single
test case and is roughly defined like this.

```scala
case class Test(
  name: String,
  body: () => TestValue,
  tags: Set[Tag],
  location: Location
)
abstract class Suite {
  def munitTests(): Seq[Test]
}
```

A test suite returns a `Seq[Test]`, which you as a user can generate and
abstract over any way you like.

Importantly, MUnit test cases are not discovered via runtime reflection like in
JUnit and MUnit test cases are not generated via macros like in utest.

MUnit provides a high-level API to write tests in a ScalaTest-inspired
`FunSuite` syntax.

```scala
abstract class FunSuite extends Suite
  with Assertions
  with Fixtures
  // with ...
```

For common usage of MUnit you are not expected to write raw `Test[T](...)`
expressions but knowing this underlying data model helps you implement features
like test retries, disabling tests based on dynamic conditions, enforce stricter
type safety and more.

## Rich filtering capabilities

Using tags, MUnit provides a extensible way to disable/enable tests based on
static and dynamic conditions.

For example, the MUnit codebase itself is cross-built against 11 different
combinations of Scala compiler versions (2.11, 2.12, 2.13, Dotty) and platforms
(JVM,JS,Native). Our CI also runs tests on JDK 8/11 and Linux/Windows.
Inevitably, some test cases end up getting disabled in certain environments.

Imagine that we have test case that for some reason should only run on Windows
in Scala 2.13. We can implement a custom `Window213` tag with the following
code:

```scala
import scala.util.Properties
import munit._
object Windows213 extends Tag("Windows213")
class MySuite extends FunSuite {
  override def munitTestTransforms = super.munitTestTransforms ++ List(
    new TestTransform("Windows213", { test =>
      val isIgnored =
        test.tags(Windows213) && !(
          Properties.isWin &&
            Properties.versionNumberString.startsWith("2.13")
        )
      if (isIgnored) test.tag(Ignore)
      else test
    })
  )

  test("windows-213".tag(Windows213)) {
    // Only runs when operating system is Windows and Scala version is 2.13
  }
  test("normal test") {
    // Always runs like a normal test.
  }
}

```

By encoding the environment requirements in the test implementation, we prevent
the situation where users run `sbt test` commands that are invalid for their
active operating system or Scala version.

Check out the
[filtering tests guide](https://scalameta.org/munit/docs/filtering.html) to
learn more how to enable/disable tests with MUnit.

## Actionable errors

The design goal for MUnit error messages is to give you as much context as
possible to address the test failure. Let's consider a few concrete examples.

![Demo showing source location for failed assertion](https://i.imgur.com/goYdJhw.png)

In the image above, you can cmd+click on the
`.../test/scala/munit/DemoSuite.scala:7` path to open the failing line of code
in your editor. By highlighting the failing line of code, you also immediately
gain some understanding for why the test might be failing.

![Demo showing diff between values of a case class](https://i.imgur.com/NaAU2He.png)

In the image above, the failing `assertEquals()` displays a diff comparing two
values of a `User` case class. The "Obtained" section includes copy-paste
friendly syntax of the obtained value, which can be helpful in the common
situation when a failing test case should have passed because the expected
behavior of your program has changed.

![Demo showing diff between multiline strings](https://i.imgur.com/ZcRiR49.png)

In the image above, the failing `assertNoDiff()` includes a `stripMargin`
formatted multiline string of the obtained string. The `assertNoDiff()`
assertions is helpful for comparing multiline strings ignoring non-visible
differences such as Windows/Unix newlines, ANSI color codes and leading/trailing
whitespace.

![Demo showing how to include clues in error messages](https://i.imgur.com/Iy82OWe.png)

In the image above, the `clue(a)` helpers are used to enrich the error message
with additional information that is displayed when the assertion fails.

![Demo showing highlighted stack traces](https://i.imgur.com/iosErEv.png)

In the image above, stack trace elements that are defined from library
dependencies like the standard library are grayed out making it easier to find
stack trace elements that are relevant for your code. This can be helpful when
debugging large exception stack traces. This feature is inspired by the
pretty-printing of stack traces in [utest](https://github.com/lihaoyi/utest).

Check out the
[writing assertions guide](https://scalameta.org/munit/docs/assertions.html) to
learn more how to write assertions with helpful error messages.

## Tooling integrations

The tooling side of a testing library is equally important as the library APIs.
MUnit is implemented as a JUnit runner, which means that any existing tool that
knows how to run a JUnit test suite knows how to run MUnit test suites.

For example, IntelliJ already detects MUnit test suites even if IntelliJ has no
custom logic to support MUnit.

![Demo showing IntelliJ running MUnit tests](https://camo.githubusercontent.com/2965bd83df7b98dbc2734815c5bcbe3e784f6242/68747470733a2f2f692e696d6775722e636f6d2f6f4141325a65512e706e67)

Likewise, build tools such as Gradle and Pants can integrate with MUnit using
their existing JUnit integrations.

## Insightful test reports

MUnit has an sbt plugin to store structured JSON data about test results in
Google Cloud. The data can then be used to generate HTML reports based on
historical test data.

The image below shows test cases in the
[Metals codebase](https://scalameta.org/metals/docs/contributors/tests.html)
sorted by how frequently they fail on the `master` branch.
[![Example HTML test report based on historical data](https://i.imgur.com/UuxYnSa.png)](https://scalameta.org/metals/docs/contributors/tests.html)

> Click on image to open full report

The Metals test suite ignores failures in tests that are tagged as flaky.
However, it's clear that `DefinitionLspSuite.missing-compiler-plugin` is not
flaky, it consistently fails on every run. On the other hand,
`PantsLspSuite.basic` has only failed once out of eleven test runs so it seems
to be legitimately flaky.

The Metals codebase has ~1.5k test cases, some which run against up to seven
different Scala compiler versions. It's not ideal that some test cases fail
non-deterministically but it's normal that it happens as the project grows and
we support more build tools, Scala versions and features. While there is no
silver bullet for avoiding flaky test failures, having data about how frequently
a test fails is at least a starting point to begin addressing the problem.

Check out the
[generating test reports guide](https://scalameta.org/munit/docs/reports.html)
to learn how to configure your build to upload test reports to Google Cloud
using the MUnit sbt plugin. The plugin is implemented as an sbt `TestsListener`
so should work with any testing library (including ScalaTest, utest, ...)
although it has so far only been tested against MUnit.

## Credits

I want to thank [@gabro](https://twitter.com/gabro27/) for implementing Dotty
support, porting the Metals codebase to MUnit and sharing tons of valuable
feedback. Without your initial interest in MUnit I probably would not have
polished the project for a proper release.

## Conclusion

MUnit is a new Scala testing library with a focus on actionable errors and
extensible APIs. MUnit is already used in several Scalameta projects including
[scalameta/scalameta](https://github.com/scalameta/scalameta),
[scalameta/metals](https://github.com/scalameta/metals) and
[scalameta/mdoc](https://github.com/scalameta/mdoc).

Most of the ideas in this post are not new. The features in MUnit are heavily
inspired by existing testing libraries including ScalaTest, utest, JUnit and ava
(a JavaScript testing library). However, I'm not aware of a testing library that
provides the combination of all the features presented in this post in one
solution and I hope that explains the motivation for why MUnit exists.

Happy testing ✌️
