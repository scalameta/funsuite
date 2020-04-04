package munit

class FrameworkSuite extends BaseFrameworkSuite {
  val tests: List[FrameworkTest] = List[FrameworkTest](
    SwallowedExceptionSuite,
    InterceptFrameworkSuite,
    CiOnlyFrameworkSuite,
    DiffProductFrameworkSuite,
    FailFrameworkSuite,
    FailSuiteFrameworkSuite,
    TestNameFrameworkSuite,
    ScalaVersionFrameworkSuite,
    FixtureFrameworkSuite,
    TagsIncludeFramweworkSuite,
    TagsIncludeExcludeFramweworkSuite,
    TagsExcludeFramweworkSuite,
    SuiteTransformCrashFrameworkSuite,
    SuiteTransformFrameworkSuite,
    TestTransformCrashFrameworkSuite,
    TestTransformFrameworkSuite,
    ValueTransformCrashFrameworkSuite,
    ValueTransformFrameworkSuite,
    ScalaCheckFrameworkSuite,
    HedgehogFrameworkSuite
  )
  tests.foreach { t => check(t) }
}
