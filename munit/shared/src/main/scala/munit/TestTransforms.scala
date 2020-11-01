package munit

import munit.internal.FutureCompat._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future
import scala.util.control.NonFatal

trait TestTransforms { this: FunSuite =>

  final class TestTransform(val name: String, fn: Test => Test)
      extends Function1[Test, Test] {
    def apply(v1: Test): Test = fn(v1)
  }

  def munitTestTransforms: List[TestTransform] =
    List(
      munitFailTransform,
      munitFlakyTransform
    )

  final def munitTestTransform(test: Test): Test = {
    try {
      munitTestTransforms.foldLeft(test) { case (t, fn) =>
        fn(t)
      }
    } catch {
      case NonFatal(e) =>
        test.withBody(() => Future.failed(e))
    }
  }

  final def munitFailTransform: TestTransform =
    new TestTransform(
      "fail",
      { t =>
        if (t.tags(Fail)) {
          t.withBodyMap(
            _.transformCompat {
              case Success(value) =>
                Failure(
                  throw new FailException(
                    munitLines.formatLine(
                      t.location,
                      "expected failure but test passed"
                    ),
                    t.location
                  )
                )
              case Failure(exception) =>
                Success(())
            }(munitExecutionContext)
          )
        } else {
          t
        }
      }
    )

  def munitFlakyOK: Boolean = "true" == System.getenv("MUNIT_FLAKY_OK")
  final def munitFlakyTransform: TestTransform =
    new TestTransform(
      "flaky",
      { t =>
        if (t.tags(Flaky)) {
          t.withBodyMap(_.transformCompat {
            case Success(value) => Success(value)
            case Failure(exception) =>
              if (munitFlakyOK) {
                Success(new TestValues.FlakyFailure(exception))
              } else {
                throw exception
              }
          }(munitExecutionContext))
        } else {
          t
        }
      }
    )

}
