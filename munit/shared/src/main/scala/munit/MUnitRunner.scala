package munit

import munit.internal.junitinterface.Configurable
import munit.internal.PlatformCompat
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import java.lang.reflect.Modifier

import munit.internal.FutureCompat._
import munit.internal.console.StackTraces
import org.junit.runner.notification.Failure

import scala.util.control.NonFatal
import org.junit.runner.manipulation.Filterable
import org.junit.runner.manipulation.Filter
import org.junit.runner.Runner
import org.junit.AssumptionViolatedException

import scala.collection.mutable
import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.util.concurrent.ExecutionException
import munit.internal.junitinterface.Settings

class MUnitRunner(val cls: Class[_ <: Suite], newInstance: () => Suite)
    extends Runner
    with Filterable
    with Configurable {

  def this(cls: Class[_ <: Suite]) =
    this(MUnitRunner.ensureEligibleConstructor(cls), () => cls.newInstance())

  val suite: Suite = newInstance()

  private implicit val ec: ExecutionContext = suite.munitExecutionContext

  @volatile private var settings: Settings = Settings.defaults()
  @volatile private var suiteAborted: Boolean = false

  private val descriptions: mutable.Map[Test, Description] =
    mutable.Map.empty[Test, Description]
  private val testNames: mutable.Set[String] =
    mutable.Set.empty[String]

  private lazy val munitTests: mutable.ArrayBuffer[Test] =
    mutable.ArrayBuffer[Test](suite.munitTests(): _*)

  override def filter(filter: Filter): Unit = {
    val newTests = munitTests.filter { t =>
      filter.shouldRun(createTestDescription(t))
    }
    munitTests.clear()
    munitTests ++= newTests
  }
  override def configure(settings: Settings): Unit = {
    this.settings = settings
  }

  def createTestDescription(test: Test): Description = {
    descriptions.getOrElseUpdate(
      test, {
        val escapedName = test.name.replace("\n", "\\n")
        val testName = munit.internal.Compat.LazyList
          .from(0)
          .map {
            case 0 => escapedName
            case n => s"${escapedName}-${n}"
          }
          .find(candidate => !testNames.contains(candidate))
          .head
        testNames += testName
        val desc = Description.createTestDescription(
          cls,
          testName,
          test.annotations: _*
        )
        desc
      }
    )
  }

  override def getDescription: Description = {
    val description = Description.createSuiteDescription(cls)

    try {
      val suiteTests = StackTraces.dropOutside(munitTests)
      suiteTests.iterator
        .map(createTestDescription)
        .foreach(description.addChild)
    } catch {
      case ex: Throwable =>
        trimStackTrace(ex)
        // Print to stdout because we don't have access to a RunNotifier
        ex.printStackTrace()
    }

    description
  }

  override def run(notifier: RunNotifier): Unit = {
    Await.result(runAsync(notifier), Duration.Inf)
  }
  def runAsync(notifier: RunNotifier): Future[Unit] = {
    val description = getDescription
    notifier.fireTestSuiteStarted(description)
    try {
      runAll(notifier)
    } catch {
      case ex: Throwable =>
        Future.successful(
          fireHiddenTest(notifier, "expected error running tests", ex)
        )
    } finally {
      notifier.fireTestSuiteFinished(description)
    }
  }

  private def runAsyncTestsSynchronously(
      notifier: RunNotifier
  ): Future[Unit] = {
    def loop(it: Iterator[Test]): Future[Unit] =
      if (!it.hasNext) {
        Future.successful(())
      } else {
        val future = runTest(notifier, it.next())
        future.value match {
          case Some(_) =>
            // use tail-recursive call if possible to keep stack traces clean.
            loop(it)
          case None =>
            future.flatMap(_ => loop(it))
        }
      }
    loop(munitTests.iterator)
  }

  private def runAll(notifier: RunNotifier): Future[Unit] = {
    if (PlatformCompat.isIgnoreSuite(cls)) {
      val description = getDescription
      notifier.fireTestIgnored(description)
      return Future.successful(())
    }
    var isBeforeAllRun = false
    val result = {
      val isContinue = runBeforeAll(notifier)
      isBeforeAllRun = isContinue
      if (isContinue) {
        runAsyncTestsSynchronously(notifier)
      } else {
        Future.successful(())
      }
    }
    result.transformCompat { s =>
      if (isBeforeAllRun) {
        runAfterAll(notifier)
      }
      s
    }
  }

  private def runBeforeAll(notifier: RunNotifier): Boolean = {
    var isContinue = runHiddenTest(notifier, "beforeAll", suite.beforeAll())
    suite.munitFixtures.foreach { fixture =>
      isContinue &= runHiddenTest(
        notifier,
        s"beforeAllFixture(${fixture.fixtureName})",
        fixture.beforeAll()
      )
    }
    isContinue
  }
  private def runAfterAll(notifier: RunNotifier): Unit = {
    suite.munitFixtures.foreach { fixture =>
      runHiddenTest(
        notifier,
        s"afterAllFixture(${fixture.fixtureName})",
        fixture.afterAll()
      )
    }
    runHiddenTest(notifier, "afterAll", suite.afterAll())
  }

  class BeforeEachResult(
      val error: Option[Throwable],
      val loadedFixtures: List[Fixture[_]]
  )
  private def runBeforeEach(
      test: Test
  ): BeforeEachResult = {
    val beforeEach = new BeforeEach(test)
    val fixtures = mutable.ListBuffer.empty[Fixture[_]]
    val error = foreachUnsafe(
      List(() => suite.beforeEach(beforeEach)) ++
        suite.munitFixtures.map(fixture =>
          () => {
            fixture.beforeEach(beforeEach)
            fixtures += fixture
            ()
          }
        )
    )
    new BeforeEachResult(error.failed.toOption, fixtures.toList)
  }

  private def runAfterEach(
      test: Test,
      fixtures: List[Fixture[_]]
  ): Unit = {
    val afterEach = new AfterEach(test)
    val error = foreachUnsafe(
      fixtures.map(fixture => () => fixture.afterEach(afterEach)) ++
        List(() => suite.afterEach(afterEach))
    )
    error.get // throw exception if it exists.
  }

  private def runTest(
      notifier: RunNotifier,
      test: Test
  ): Future[Boolean] = {
    val description = createTestDescription(test)

    if (suiteAborted) {
      notifier.fireTestAssumptionFailed(
        new Failure(
          description,
          new FailSuiteException("Suite has been aborted", test.location)
        )
      )
      return Future.successful(false)
    }

    notifier.fireTestStarted(description)
    if (test.tags(Ignore)) {
      notifier.fireTestIgnored(description)
      return Future.successful(false)
    }
    val onError: PartialFunction[Throwable, Future[Unit]] = {
      case ex: AssumptionViolatedException =>
        trimStackTrace(ex)
        Future.successful(())
      case NonFatal(ex) =>
        trimStackTrace(ex)
        val cause = ex match {
          case e: ExecutionException if e.getCause() != null && {
                e.getMessage() match {
                  // NOTE(olafur): these exceptions appear when we await on
                  // futures. We unwrap these exception in order to provide more
                  // helpful error messages.
                  case "Boxed Exception" | "Boxed Error" => true
                  case _                                 => false
                }
              } =>
            e.getCause()
          case e => e
        }
        val failure = new Failure(description, cause)
        cause match {
          case _: AssumptionViolatedException =>
            notifier.fireTestAssumptionFailed(failure)
          case _: FailSuiteException =>
            suiteAborted = true
            notifier.fireTestFailure(failure)
          case _ =>
            notifier.fireTestFailure(failure)
        }
        Future.successful(())
    }
    val result: Future[Unit] =
      try runTestBody(notifier, description, test).recoverWith(onError)
      catch onError
    result.map { _ =>
      notifier.fireTestFinished(description)
      true
    }
  }

  private def futureFromAny(any: Any): Future[Any] = any match {
    case f: Future[_] => f
    case _            => Future.successful(any)
  }

  private def runTestBody(
      notifier: RunNotifier,
      description: Description,
      test: Test
  ): Future[Unit] = {
    val result: Future[Any] = StackTraces.dropOutside {
      val beforeEachResult = runBeforeEach(test)
      val any = beforeEachResult.error match {
        case None =>
          try test.body()
          finally runAfterEach(test, beforeEachResult.loadedFixtures)
        case Some(error) =>
          try runAfterEach(test, beforeEachResult.loadedFixtures)
          finally throw error
      }
      futureFromAny(any)
    }
    result.map {
      case f: TestValues.FlakyFailure =>
        trimStackTrace(f)
        notifier.fireTestAssumptionFailed(new Failure(description, f))
      case TestValues.Ignore =>
        notifier.fireTestIgnored(description)
      case _ =>
        ()
    }
  }

  private def foreachUnsafe(thunks: Iterable[() => Unit]): Try[Unit] = {
    var errors = mutable.ListBuffer.empty[Throwable]
    thunks.foreach { thunk =>
      try {
        thunk()
      } catch {
        case ex if NonFatal(ex) =>
          errors += ex
      }
    }
    errors.toList match {
      case head :: tail =>
        tail.foreach { e =>
          if (e ne head) {
            head.addSuppressed(e)
          }
        }
        scala.util.Failure(head)
      case _ =>
        scala.util.Success(())
    }
  }

  private def runHiddenTest(
      notifier: RunNotifier,
      name: String,
      thunk: => Unit
  ): Boolean = {
    try {
      StackTraces.dropOutside(thunk)
      true
    } catch {
      case ex: Throwable =>
        fireHiddenTest(notifier, name, ex)
        false
    }
  }

  private def fireHiddenTest(
      notifier: RunNotifier,
      name: String,
      ex: Throwable
  ): Unit = {
    val test = new Test(name, () => ???, Set.empty, Location.empty)
    val description = createTestDescription(test)
    notifier.fireTestStarted(description)
    trimStackTrace(ex)
    notifier.fireTestFailure(new Failure(description, ex))
    notifier.fireTestFinished(description)
  }
  private def trimStackTrace(ex: Throwable): Unit = {
    if (settings.trimStackTraces()) {
      StackTraces.trimStackTrace(ex)
    }
  }

}
object MUnitRunner {
  private def ensureEligibleConstructor(
      cls: Class[_ <: Suite]
  ): Class[_ <: Suite] = {
    require(
      hasEligibleConstructor(cls),
      s"Class '${cls.getName}' is missing a public empty argument constructor"
    )
    cls
  }
  private def hasEligibleConstructor(cls: Class[_ <: Suite]): Boolean = {
    try {
      val constructor = cls.getConstructor(
        new Array[java.lang.Class[_]](0): _*
      )
      Modifier.isPublic(constructor.getModifiers)
    } catch {
      case nsme: NoSuchMethodException => false
    }
  }
}
