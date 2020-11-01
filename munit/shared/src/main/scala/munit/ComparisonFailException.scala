package munit

import org.junit.ComparisonFailure

/**
 * The base exception for all comparison failures.
 *
 * This class exists so that it can extend `org.junit.ComparisonFailure`,
 * which is recognised by IntelliJ so that users can optionally compare the
 * obtained/expected values in a GUI diff explorer.
 *
 * @param message the exception message.
 * @param obtained the obtained value from this comparison.
 * @param obtainedString the pretty-printed representation of the obtained value.
 *                       This string is displayed in the IntelliJ diff viewer.
 * @param expected the expected value from this comparison.
 * @param expectedString the pretty-printed representation of the obtained value.
 *                       This string is displayed in the IntelliJ diff viewer.
 * @param location the source location where this exception was thrown.
 */
class ComparisonFailException(
    val message: String,
    val obtained: Any,
    val obtainedString: String,
    val expected: Any,
    val expectedString: String,
    val location: Location,
    val isStackTracesEnabled: Boolean
) extends ComparisonFailure(message, expectedString, obtainedString)
    with FailExceptionLike[ComparisonFailException] {
  def this(
      message: String,
      obtained: Any,
      expected: Any,
      location: Location,
      isStackTracesEnabled: Boolean
  ) = this(
    message,
    obtained,
    s"$obtained",
    expected,
    s"$expected",
    location,
    isStackTracesEnabled
  )
  override def getMessage: String = message
  def withMessage(newMessage: String): ComparisonFailException =
    new ComparisonFailException(
      newMessage,
      obtained,
      obtainedString,
      expected,
      expectedString,
      location,
      isStackTracesEnabled
    )
  override def fillInStackTrace(): Throwable = {
    val result = super.fillInStackTrace()
    if (!isStackTracesEnabled) {
      result.setStackTrace(result.getStackTrace().slice(0, 1))
    }
    result
  }
}
