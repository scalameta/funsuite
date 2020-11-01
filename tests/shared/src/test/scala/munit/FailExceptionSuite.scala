package munit

class FailExceptionSuite extends BaseSuite {
  test("assertion-error") {
    val e = intercept[AssertionError] {
      fail("hello world!")
    }
    assert(clue(e).isInstanceOf[Serializable])
  }
}
