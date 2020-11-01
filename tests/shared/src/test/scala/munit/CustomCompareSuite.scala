package munit

class CustomCompareSuite extends BaseSuite {
  import CustomCompare.fromCustomEquality
  test("ok") {
    assertEquals(Some(1), Option(1))
  }

  test("boom") {
    interceptMessage[RuntimeException]("boom")(
      assertEquals(Some(42), Option(42))
    )
  }

  test("fallback-to-default") {
    // NOTE: Users who rely on custom equality won't get a compile error for
    // comparisons between supertype/subtype relationships. They only get a
    // compile error when comparing unrelated types (same as default behavior).
    assertEquals(List(1), collection.Seq(1))
  }
}
