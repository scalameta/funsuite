package munit

import hedgehog._
import hedgehog.core.{PropertyConfig, PropertyT, Seed}
import hedgehog.predef.Monad

import munit.internal.Compat._

/**
 * Provides the ability to write property based tests using the Hedgehog library.
 *
 * Properties are defined by calling one of the various `property` or `propertyF`
 * methods and passing a `PropertyT[Result]` or `PropertyT[F[Result]]` respectively.
 * For example:
 * {{{
 * property("additive identity") {
 *   Gen.int(Range.linearFrom(0, Int.MinValue, Int.MaxValue)).forAll.map { n =>
 *     assertEquals(n + 0, n)
 *   }
 * }
 * }}}
 *
 * Note both Hedgehog `Result` and munit assertions are supported.
 */
trait HedgehogSuite extends FunSuite {

  /**
   * Provides the default configuration for running property tests.
   * This can be overriden to change the default or can be changed on
   * a test-by-test basis by using an overload of `property` and `propertyF`.
   */
  protected def hedgehogPropertyConfig: PropertyConfig = PropertyConfig.default
  protected def hedgehogSeed: Long = System.currentTimeMillis()

  def property(
      name: String
  )(prop: PropertyT[Result])(implicit loc: Location): Unit =
    property(new TestOptions(name, Set.empty, loc))(prop)

  def property(
      options: TestOptions
  )(prop: PropertyT[Result])(implicit loc: Location): Unit = {
    val config = options.tags.collectFirst { case HedgehogConfig(config) => config }.getOrElse(hedgehogPropertyConfig)
    test(options)(check(prop, config, hedgehogSeed))
  }

  def propertyF[F[_]: Monad](
      name: String
  )(prop: PropertyT[F[Result]])(implicit loc: Location): Unit =
    propertyF(new TestOptions(name, Set.empty, loc))(prop)

  def propertyF[F[_]: Monad](
      options: TestOptions
  )(prop: PropertyT[F[Result]])(implicit loc: Location): Unit = {
    val config = options.tags.collectFirst { case HedgehogConfig(config) => config }.getOrElse(hedgehogPropertyConfig)
    test(options)(checkF(prop, config, hedgehogSeed))
  }

  /**
   * Checks the supplied `Property[Result]`, throwing a `HedgehogFailException`
   * if the property was falsified.
   */
  private def check(
      prop: PropertyT[Result],
      config: PropertyConfig = hedgehogPropertyConfig,
      seed: Long = hedgehogSeed
  )(implicit loc: Location): Unit = {
    val report = Property.check(config, prop, Seed.fromLong(seed))
    HedgehogFailException.fromReport(report, seed) match {
      case None    => ()
      case Some(t) => throw t
    }
  }

  /**
   * Checks the supplied `PropertyT[F[Result]]`, throwing a `HedgehogFailException`
   * if the property was falsified.
   *
   * Note: the exception is thrown within a call to `map` on the effect type. Hence,
   * this should only be used with effect types that handle exceptions thrown from
   * `map`.
   */
  private def checkF[F[_]: Monad](
      prop: PropertyT[F[Result]],
      config: PropertyConfig = hedgehogPropertyConfig,
      seed: Long = hedgehogSeed
  )(implicit loc: Location): F[Unit] = {
    ??? // Waiting for https://github.com/hedgehogqa/scala-hedgehog/pull/147
  }

  /**
   * Supports writing properties with munit assertions instead of Hedgehog `Result`s.
   */
  implicit val unitToResult: Conversion[Unit, Result] = _ => Result.Success
}
