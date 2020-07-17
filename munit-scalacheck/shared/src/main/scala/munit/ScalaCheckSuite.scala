package munit

import org.scalacheck.{Prop, Properties}
import org.scalacheck.{Test => ScalaCheckTest}
import org.scalacheck.util.Pretty
import org.scalacheck.rng.Seed
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import munit.internal.FutureCompat._

trait ScalaCheckSuite extends FunSuite {

  def property(
      name: String
  )(body: => Prop)(implicit loc: Location): Unit = {
    property(new TestOptions(name, Set.empty, loc))(body)
  }

  def property(
      options: TestOptions
  )(body: => Prop)(implicit loc: Location): Unit = {
    test(options)(body)
  }

  /** Adds all properties from another property collection to this one */
  def include(ps: Properties): Unit =
    include(ps, prefix = "")

  /** Adds all properties from another property collection to this one
   *  with a prefix this is prepended to each included property's name. */
  def include(ps: Properties, prefix: String): Unit =
    for ((n, p) <- ps.properties) property(prefix + n)(p)

  // Allow property bodies of type Unit
  // This is done to support using MUnit assertions in property bodies
  // instead of returning a Boolean.
  implicit def unitToProp: Unit => Prop = _ => Prop.passed

  override def munitTestTransforms: List[TestTransform] =
    super.munitTestTransforms :+ scalaCheckPropTransform

  protected def scalaCheckTestParameters = ScalaCheckTest.Parameters.default

  protected def scalaCheckPrettyParameters = Pretty.defaultParams

  protected def scalaCheckInitialSeed: String = Seed.random().toBase64

  private val scalaCheckPropTransform: TestTransform =
    new TestTransform("ScalaCheck Prop", t => {
      t.withBodyMap[TestValue](
        _.transformCompat {
          case Success(prop: Prop) => propToTry(prop, t)
          case r                   => r
        }(munitExecutionContext)
      )
    })

  private def propToTry(prop: Prop, test: Test): Try[Unit] = {
    import ScalaCheckTest._
    def seed =
      scalaCheckTestParameters.initialSeed.getOrElse(
        Seed.fromBase64(scalaCheckInitialSeed).get
      )
    val result = check(
      scalaCheckTestParameters,
      Prop(genParams => prop(genParams.withInitialSeed(seed)))
    )
    def renderResult(r: Result): String = {
      val resultMessage = Pretty.pretty(r, scalaCheckPrettyParameters)
      if (r.passed) {
        resultMessage
      } else {
        val seedMessage = s"""|Failing seed: ${seed.toBase64}
                              |You can reproduce this failure by adding the following override to your suite:
                              |
                              |  override val scalaCheckInitialSeed = "${seed.toBase64}"
                              |""".stripMargin
        seedMessage + "\n" + resultMessage
      }
    }

    result.status match {
      case Passed | Proved(_) =>
        Success(())
      case status @ PropException(_, e, _) =>
        e match {
          case f: FailException =>
            // Promote FailException (i.e failed assertions) to property failures
            val r = result.copy(status = Failed(status.args, status.labels))
            Failure(f.withMessage(e.getMessage() + "\n\n" + renderResult(r)))
          case _ =>
            Failure(
              new FailException(
                message = renderResult(result),
                cause = e,
                isStackTracesEnabled = false,
                location = test.location
              )
            )
        }
      case _ =>
        // Fail using the test location
        Try(fail("\n" + renderResult(result))(test.location))
    }
  }

}
