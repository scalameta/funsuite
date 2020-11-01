package munit

import org.junit.ComparisonFailure
import munit.internal.console.Lines
import java.nio.file.Paths

class ComparisonFailExceptionSuite extends BaseSuite {
  override val munitLines: Lines = new Lines {
    override def formatPath(location: Location): String = {
      Paths.get(location.path).getFileName().toString()
    }
  }
  test("comparison-failure") {
    val e = intercept[ComparisonFailException] {
      assertEquals[Any, Any](List("1", "2", "3"), List(1, 2))
    }
    assert(clue(e).isInstanceOf[ComparisonFailure])
    assert(clue(e).isInstanceOf[Serializable])
    // NOTE: assert that we use the `toString` of values in the
    // `org.junit.ComparisionFailure` exception. The stdout message in the
    // console still uses `munitPrint()`, which would have displayed `List("1",
    // "2", "3")` instead of `List(1, 2, 3)`.
    assertNoDiff(
      e.getActual,
      "List(1, 2, 3)"
    )
    assertNoDiff(
      e.getExpected,
      "List(1, 2)"
    )
    assertEquals(e.expected, List(1, 2))
    assertEquals(e.obtained, List("1", "2", "3"))
    assertNoDiff(
      e.getMessage(),
      """|ComparisonFailExceptionSuite.scala:15
         |14:    val e = intercept[ComparisonFailException] {
         |15:      assertEquals[Any, Any](List("1", "2", "3"), List(1, 2))
         |16:    }
         |values are not the same
         |=> Obtained
         |List(
         |  "1",
         |  "2",
         |  "3"
         |)
         |=> Diff (- obtained, + expected)
         | List(
         |-  "1",
         |-  "2",
         |-  "3"
         |+  1,
         |+  2
         | )
         |""".stripMargin
    )
  }

}
