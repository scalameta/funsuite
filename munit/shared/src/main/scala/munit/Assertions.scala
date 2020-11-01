package munit

import munit.internal.console.{Lines, Printers, StackTraces}
import munit.internal.difflib.ComparisonFailExceptionHandler
import munit.internal.difflib.Diffs

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.collection.mutable
import munit.internal.console.AnsiColors
import org.junit.AssumptionViolatedException
import munit.internal.MacroCompat

object Assertions extends Assertions
trait Assertions extends MacroCompat.CompileErrorMacro {

  val munitLines = new Lines

  def munitAnsiColors: Boolean = true

  private def munitFilterAnsi(message: String): String =
    if (munitAnsiColors) message
    else AnsiColors.filterAnsi(message)

  def assert(
      cond: => Boolean,
      clue: => Any = "assertion failed"
  )(implicit loc: Location): Unit = {
    StackTraces.dropInside {
      val (isTrue, clues) = munitCaptureClues(cond)
      if (!isTrue) {
        fail(munitPrint(clue), clues)
      }
    }
  }

  def assume(
      cond: Boolean,
      clue: => Any = "assumption failed"
  )(implicit loc: Location): Unit = {
    StackTraces.dropInside {
      if (!cond) {
        throw new AssumptionViolatedException(munitPrint(clue))
      }
    }
  }

  def assertNoDiff(
      obtained: String,
      expected: String,
      clue: => Any = "diff assertion failed"
  )(implicit loc: Location): Unit = {
    StackTraces.dropInside {
      Diffs.assertNoDiff(
        obtained,
        expected,
        ComparisonFailExceptionHandler.fromAssertions(this, Clues.empty),
        munitPrint(clue),
        printObtainedAsStripMargin = true
      )
    }
  }

  /**
   * Asserts that two elements are not equal according to the `Compare[A, B]` type-class.
   *
   * By default, uses `==` to compare values.
   */
  def assertNotEquals[A, B](
      obtained: A,
      expected: B,
      clue: => Any = "values are the same"
  )(implicit loc: Location, compare: Compare[A, B]): Unit = {
    StackTraces.dropInside {
      if (compare.isEqual(obtained, expected)) {
        failComparison(
          s"${munitPrint(clue)} expected same: $expected was not: $obtained",
          obtained,
          expected
        )
      }
    }
  }

  /**
   * Asserts that two elements are equal according to the `Compare[A, B]` type-class.
   *
   * By default, uses `==` to compare values.
   */
  def assertEquals[A, B](
      obtained: A,
      expected: B,
      clue: => Any = "values are not the same"
  )(implicit loc: Location, compare: Compare[A, B]): Unit = {
    StackTraces.dropInside {
      if (!compare.isEqual(obtained, expected)) {
        compare.failEqualsComparison(obtained, expected, clue, loc, this)
      }
    }
  }

  /**
   * Asserts that two doubles are equal to within a positive delta.
   * If the expected value is infinity then the delta value is ignored.
   * NaNs are considered equal: assertEquals(Double.NaN, Double.NaN, *) passes.
   */
  def assertEqualsDouble(
      obtained: Double,
      expected: Double,
      delta: Double,
      clue: => Any = "values are not the same"
  )(implicit loc: Location): Unit = {
    StackTraces.dropInside {
      val exactlyTheSame = java.lang.Double.compare(expected, obtained) == 0
      val almostTheSame = Math.abs(expected - obtained) <= delta
      if (!exactlyTheSame && !almostTheSame) {
        failComparison(
          s"${munitPrint(clue)} expected: $expected but was: $obtained",
          obtained,
          expected
        )
      }
    }
  }

  /**
   * Asserts that two floats are equal to within a positive delta.
   * If the expected value is infinity then the delta value is ignored.
   * NaNs are considered equal: assertEquals(Float.NaN, Float.NaN, *) passes.
   */
  def assertEqualsFloat(
      obtained: Float,
      expected: Float,
      delta: Float,
      clue: => Any = "values are not the same"
  )(implicit loc: Location): Unit = {
    StackTraces.dropInside {
      val exactlyTheSame = java.lang.Float.compare(expected, obtained) == 0
      val almostTheSame = Math.abs(expected - obtained) <= delta
      if (!exactlyTheSame && !almostTheSame) {
        failComparison(
          s"${munitPrint(clue)} expected: $expected but was: $obtained",
          obtained,
          expected
        )
      }
    }
  }

  /**
   * Evalutes the given expression and asserts that an exception of type T is thrown.
   */
  def intercept[T <: Throwable](
      body: => Any
  )(implicit T: ClassTag[T], loc: Location): T = {
    runIntercept(None, body)
  }

  /**
   * Evalutes the given expression and asserts that an exception of type T with the expected message is thrown.
   */
  def interceptMessage[T <: Throwable](expectedExceptionMessage: String)(
      body: => Any
  )(implicit T: ClassTag[T], loc: Location): T = {
    runIntercept(Some(expectedExceptionMessage), body)
  }

  private def runIntercept[T <: Throwable](
      expectedExceptionMessage: Option[String],
      body: => Any
  )(implicit T: ClassTag[T], loc: Location): T = {
    try {
      body
      fail(
        s"expected exception of type '${T.runtimeClass.getName()}' but body evaluated successfully"
      )
    } catch {
      case e: FailExceptionLike[_]
          if !T.runtimeClass.isAssignableFrom(e.getClass()) =>
        throw e
      case NonFatal(e) =>
        if (T.runtimeClass.isAssignableFrom(e.getClass())) {
          if (
            expectedExceptionMessage.isEmpty || e.getMessage == expectedExceptionMessage.get
          )
            e.asInstanceOf[T]
          else {
            val obtained = e.getClass().getName()
            throw new FailException(
              s"intercept failed, exception '$obtained' had message '${e.getMessage}', which was different from expected message '${expectedExceptionMessage.get}'",
              cause = e,
              isStackTracesEnabled = false,
              location = loc
            )
          }
        } else {
          val obtained = e.getClass().getName()
          val expected = T.runtimeClass.getName()
          throw new FailException(
            s"intercept failed, exception '$obtained' is not a subtype of '$expected",
            cause = e,
            isStackTracesEnabled = false,
            location = loc
          )
        }
    }
  }

  /**
   * Unconditionally fails this test with the given message and exception marked as the cause.
   */
  def fail(message: String, cause: Throwable)(implicit
      loc: Location
  ): Nothing = {
    throw new FailException(
      munitFilterAnsi(munitLines.formatLine(loc, message)),
      cause,
      isStackTracesEnabled = true,
      location = loc
    )
  }

  /**
   * Unconditionally fails this test with the given message and optional clues.
   */
  def fail(
      message: String,
      clues: Clues = new Clues(Nil)
  )(implicit loc: Location): Nothing = {
    throw new FailException(
      munitFilterAnsi(munitLines.formatLine(loc, message, clues)),
      loc
    )
  }

  /**
   * Unconditionally fails this test due to result of comparing two values.
   *
   * The only reason to use this method instead of `fail()` is if you want to
   * allow comparing the two different values in the the IntelliJ GUI diff
   * viewer.
   */
  def failComparison(
      message: String,
      obtained: Any,
      expected: Any,
      clues: Clues = new Clues(Nil)
  )(implicit loc: Location): Nothing = {
    throw new ComparisonFailException(
      munitFilterAnsi(munitLines.formatLine(loc, message, clues)),
      obtained,
      expected,
      loc,
      isStackTracesEnabled = false
    )
  }

  /**
   * Unconditionally fail this test case and cancel all the subsequent tests in this suite.
   */
  def failSuite(
      message: String,
      clues: Clues = new Clues(Nil)
  )(implicit loc: Location): Nothing = {
    throw new FailSuiteException(
      munitFilterAnsi(munitLines.formatLine(loc, message, clues)),
      loc
    )
  }

  private val munitCapturedClues: mutable.ListBuffer[Clue[_]] =
    mutable.ListBuffer.empty
  def munitCaptureClues[T](thunk: => T): (T, Clues) =
    synchronized {
      munitCapturedClues.clear()
      val result = thunk
      (result, new Clues(munitCapturedClues.toList))
    }

  def clue[T](c: Clue[T]): T = synchronized {
    munitCapturedClues += c
    c.value
  }
  def clues(clue: Clue[_]*): Clues = new Clues(clue.toList)

  def munitPrint(clue: => Any): String = {
    clue match {
      case message: String => message
      case value           => Printers.print(value)
    }
  }

}
