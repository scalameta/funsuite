package munit

/**
 * @param name The name of this fixture, used for displaying an error message if
 * `beforeAll()` or `afterAll()` fail.
 */
abstract class Fixture[T](val fixtureName: String) {

  /** The value produced by this suite-local fixture that can be reused for all test cases. */
  def apply(): T

  /** Runs once before the test suite starts */
  def beforeAll(): Unit = ()

  /**
   * Runs before each individual test case.
   * An error in this method aborts the test case.
   */
  def beforeEach(context: BeforeEach): Unit = ()

  /** Runs after each individual test case. */
  def afterEach(context: AfterEach): Unit = ()

  /** Runs once after the test suite has finished, regardless if the tests failed or not. */
  def afterAll(): Unit = ()

}
