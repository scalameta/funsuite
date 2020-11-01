package object munit {
  val Ignore = new Tag("Ignore")
  val Only = new Tag("Only")
  val Flaky = new Tag("Flaky")
  val Fail = new Tag("Fail")
  val Slow = new Tag("Slow")

  type TestValue = scala.concurrent.Future[Any]

  @deprecated("use Test without a type parameter instead", "2020-10-19")
  type GenericTest[T] = Test
  @deprecated("use BeforeEach without a type parameter instead", "2020-10-19")
  type GenericBeforeEach[T] = BeforeEach
  @deprecated("use AfterEach without a type parameter instead", "2020-10-19")
  type GenericAfterEach[T] = AfterEach
}
