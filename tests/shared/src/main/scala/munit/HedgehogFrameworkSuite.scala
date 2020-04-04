package munit

import hedgehog.{Gen, Range, Result}
import hedgehog.core.SuccessCount

class HedgehogFrameworkSuite extends HedgehogSuite {

  // The default property seed changes on each test run so we fix it here so that test failures are always the same
  override val hedgehogSeed = 123L

  private val genInt: Gen[Int] =
    Gen.int(Range.linearFrom(0, Int.MinValue, Int.MaxValue))

  property("result check (true)") {
    for {
      l1 <- Gen.list(genInt, Range.linear(0, 100)).forAll
      l2 <- Gen.list(genInt, Range.linear(0, 100)).forAll
    } yield Result.assert(l1.size + l2.size == (l1 ::: l2).size)
  }

  property("result check (false)") {
    genInt.forAll.map(n => Result.assert(scala.math.sqrt(n * n) == n))
  }

  property("tagged".tag(new Tag("a"))) {
    genInt.forAll.map(n => Result.assert(n + 0 == n))
  }

  property("assertions (true)") {
    genInt.forAll.map { n =>
      assertEquals(n * 2, n + n)
      assertEquals(n * 0, 0)
    }
  }

  property("assertions (false)") {
    genInt.forAll.map { n =>
      assertEquals(n * 1, n)
      assertEquals(n * n, n)
      assertEquals(n + 0, n)
    }
  }

  property(
    "custom config".tag(HedgehogConfig(hedgehogPropertyConfig.copy(testLimit = SuccessCount(1000))))
  ) {
    genInt.forAll.map(n => assertEquals(n + 0, n))
  }
}

object HedgehogFrameworkSuite
    extends FrameworkTest(
      classOf[HedgehogFrameworkSuite],
      s"""|==> success munit.HedgehogFrameworkSuite.result check (true)
          |==> failure munit.HedgehogFrameworkSuite.result check (false) - Falsified after 0 passed tests and 24 shrinks using seed 123
          |> -1
          |==> success munit.HedgehogFrameworkSuite.tagged
          |==> success munit.HedgehogFrameworkSuite.assertions (true)
          |==> failure munit.HedgehogFrameworkSuite.assertions (false) - Falsified after 0 passed tests and 24 shrinks using seed 123
          |> -1
          |> munit.FailException: /scala/munit/HedgehogFrameworkSuite.scala:39
          |38:      assertEquals(n * 1, n)
          |39:      assertEquals(n * n, n)
          |40:      assertEquals(n + 0, n)
          |values are not the same
          |=> Obtained
          |1
          |=> Diff (- obtained, + expected)
          |-1
          |+-1
          |==> success munit.HedgehogFrameworkSuite.custom config
          |""".stripMargin
    )
