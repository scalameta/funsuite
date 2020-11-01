package munit

import scala.util.control.NonFatal
import scala.concurrent.Future

trait SuiteTransforms { this: FunSuite =>

  final class SuiteTransform(val name: String, fn: List[Test] => List[Test])
      extends Function1[List[Test], List[Test]] {
    def apply(v1: List[Test]): List[Test] = fn(v1)
  }

  def munitSuiteTransforms: List[SuiteTransform] =
    List(
      munitIgnoreSuiteTransform,
      munitOnlySuiteTransform
    )

  final def munitSuiteTransform(tests: List[Test]): List[Test] = {
    try {
      munitSuiteTransforms.foldLeft(tests) { case (ts, fn) =>
        fn(ts)
      }
    } catch {
      case NonFatal(e) =>
        List(
          new Test(
            "munitSuiteTransform",
            () => Future.failed(e)
          )(Location.empty)
        )
    }
  }

  def munitIgnore: Boolean = false
  final def munitIgnoreSuiteTransform: SuiteTransform =
    new SuiteTransform(
      "munitIgnore",
      { tests =>
        if (munitIgnore) Nil
        else tests
      }
    )

  def isCI: Boolean = "true" == System.getenv("CI")
  final def munitOnlySuiteTransform: SuiteTransform =
    new SuiteTransform(
      "only",
      { tests =>
        val onlySuite = tests.filter(_.tags(Only))
        if (onlySuite.nonEmpty) {
          if (!isCI) {
            onlySuite
          } else {
            onlySuite.map(t =>
              if (t.tags(Only)) {
                t.withBody(() =>
                  fail("'Only' tag is not allowed when `isCI=true`")(t.location)
                )
              } else {
                t
              }
            )
          }
        } else {
          tests
        }
      }
    )
}
