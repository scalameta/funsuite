package munit

/**
 * The base class for all MUnit FailExceptions.
 *
 * Implementation note: this class exists so that we could fix the issue
 * https://youtrack.jetbrains.com/issue/SCL-18255 In order to support the JUnit
 * comparison GUI in IntelliJ we need to extend org.junit.ComparisonFailure,
 * which is a class and not an interface. We can't make `munit.FailException`
 * extend `org.junit.ComparisonFailure` since not all "fail exceptions" are
 * "comparison failures". Instead, we introduced
 * `munit.ComparisionFailException`, which extends
 * `org.junit.ComparisonFailure` and this base trait. Internally, MUnit should
 * match against `FailExceptionLike[_]` instead of `munit.FailException` directly.
 */
trait FailExceptionLike[T <: AssertionError] extends Serializable {
  self: AssertionError =>
  def withMessage(message: String): T
  def location: Location
  def isStackTracesEnabled: Boolean
}
