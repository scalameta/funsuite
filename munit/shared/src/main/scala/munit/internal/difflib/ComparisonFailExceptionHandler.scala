package munit.internal.difflib

import munit.Location
import munit.Assertions
import munit.Clues

trait ComparisonFailExceptionHandler {
  def handle(
      message: String,
      obtained: String,
      expected: String,
      location: Location
  ): Nothing
}
object ComparisonFailExceptionHandler {
  def fromAssertions(
      assertions: Assertions,
      clues: => Clues
  ): ComparisonFailExceptionHandler =
    new ComparisonFailExceptionHandler {
      def handle(
          message: String,
          obtained: String,
          expected: String,
          loc: Location
      ): Nothing = {
        assertions.failComparison(message, obtained, expected, clues)(loc)
      }
    }
}
