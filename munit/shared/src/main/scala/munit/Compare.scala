package munit

import munit.internal.difflib.Diffs
import munit.internal.difflib.ComparisonFailExceptionHandler
import scala.annotation.implicitNotFound

/**
 * A type-class that is used to compare values in MUnit assertions.
 *
 * By default, uses == and allows comparison between any two types as long
 * they have a supertype/subtype relationship. For example:
 *
 * - Compare[T, T] OK
 * - Compare[Some[Int], Option[Int]] OK, subtype
 * - Compare[Option[Int], Some[Int]] OK, supertype
 * - Compare[List[Int], collection.Seq[Int]] OK, subtype
 * - Compare[List[Int], Vector[Int]] Error, requires upcast to `Seq[Int]`
 */
@implicitNotFound(
  // NOTE: Dotty ignores this message if the string is formatted as a multiline string """..."""
  "Can't compare these two types:\n  First type:  ${A}\n  Second type: ${B}\nPossible ways to fix this error:\n  Alternative 1: provide an implicit instance for Compare[${A}, ${B}]\n  Alternative 2: upcast either type into `Any` or a shared supertype"
)
trait Compare[A, B] {

  /**
   * Returns true if the values are equal according to the rules of this `Compare[A, B]` instance.
   *
   * The default implementation of this method uses `==`.
   */
  def isEqual(obtained: A, expected: B): Boolean

  /**
   * Throws an exception to fail this assertion when two values are not equal.
   *
   * Override this method to customize the error message. For example, it may
   * be helpful to generate an image/HTML file if you're comparing visual
   * values. Anything is possible, use your imagination!
   *
   * @return should ideally throw a org.junit.ComparisonFailException in order
   *         to support the IntelliJ diff viewer.
   */
  def failEqualsComparison(
      obtained: A,
      expected: B,
      title: Any,
      loc: Location,
      assertions: Assertions
  ): Nothing = {
    val diffHandler = new ComparisonFailExceptionHandler {
      override def handle(
          message: String,
          _obtained: String,
          _expected: String,
          loc: Location
      ): Nothing =
        assertions.failComparison(
          message,
          obtained,
          expected
        )(loc)
    }
    // Attempt 1: custom pretty-printer that produces multiline output, which is
    // optimized for line-by-line diffing.
    Diffs.assertNoDiff(
      assertions.munitPrint(obtained),
      assertions.munitPrint(expected),
      diffHandler,
      title = assertions.munitPrint(title),
      printObtainedAsStripMargin = false
    )(loc)

    // Attempt 2: try with `.toString` in case `munitPrint()` produces identical
    // formatting for both values.
    Diffs.assertNoDiff(
      obtained.toString(),
      expected.toString(),
      diffHandler,
      title = assertions.munitPrint(title),
      printObtainedAsStripMargin = false
    )(loc)

    // Attempt 3: string comparison is not working, unconditionally fail the test.
    assertions.failComparison(
      s"values are not equal even if they have the same `toString()`: $obtained",
      obtained,
      expected
    )(loc)
  }

}

object Compare extends ComparePriority1 {
  private val anyEquality: Compare[Any, Any] = _ == _
  def defaultCompare[A, B]: Compare[A, B] =
    anyEquality.asInstanceOf[Compare[A, B]]
}

/** Allows comparison between A and B when A is a subtype of B */
trait ComparePriority1 extends ComparePriority2 {
  implicit def compareSubtypeWithSupertype[A, B](implicit
      ev: A <:< B
  ): Compare[A, B] = Compare.defaultCompare
}

/**
 * Allows comparison between A and B when B is a subtype of A.
 *
 * This implicit is defined separately from ComparePriority1 in order to avoid
 * diverging implicit search when comparing equal types.
 */
trait ComparePriority2 {
  implicit def compareSupertypeWithSubtype[A, B](implicit
      ev: A <:< B
  ): Compare[B, A] = Compare.defaultCompare
}
