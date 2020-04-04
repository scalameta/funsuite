package munit

import scala.util.control.NoStackTrace

import hedgehog.core._
import hedgehog.runner.Test

class HedgehogFailException(message: String, val report: Report, val seed: Long)
    extends Exception(message)
    with NoStackTrace

object HedgehogFailException {

  def fromReport(report: Report, seed: Long): Option[HedgehogFailException] = {
    report.status match {
      case OK =>
        None
      case Failed(shrinks, log) =>
        val coverage = Test.renderCoverage(report.coverage, report.tests)
        val message = render(
          s"Falsified after ${report.tests.value} passed tests and ${shrinks.value} shrinks using seed ${seed}",
          log.map(renderLog) ++ coverage
        )
        Some(new HedgehogFailException(message, report, seed))
      case GaveUp =>
        val coverage = Test.renderCoverage(report.coverage, report.tests)
        val message = render(
          s"Gave up after ${report.tests.value} passed tests using seed value $seed. ${report.discards.value} were discarded",
          coverage
        )
        Some(new HedgehogFailException(message, report, seed))
    }
  }

  private def render(msg: String, extras: List[String]): String =
    (msg :: extras.map(e => "> " + e)).mkString("\n")

  // From Hedgehog, but customized to *not* include the stack trace and instead print exception toString
  private def renderLog(log: Log): String = {
    log match {
      case ForAll(name, value) =>
        s"${name.value}: $value"
      case Info(value) =>
        value
      case Error(e) =>
        e.toString
    }
  }
}
