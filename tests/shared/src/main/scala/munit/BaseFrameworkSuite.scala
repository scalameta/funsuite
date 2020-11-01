package munit

import java.io.ByteArrayOutputStream
import sbt.testing.TaskDef
import sbt.testing.EventHandler
import sbt.testing.Event
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import sbt.testing.Logger
import scala.util.control.NonFatal
import java.util.regex.Pattern
import munit.internal.console.AnsiColors
import munit.internal.PlatformCompat
import scala.concurrent.Future

abstract class BaseFrameworkSuite extends BaseSuite {
  val systemOut = System.out
  override def munitIgnore: Boolean = !BuildInfo.scalaVersion.startsWith("2.13")
  def exceptionMessage(ex: Throwable): String = {
    if (ex.getMessage() == null) "null"
    else {
      ex.getMessage()
        .replace(
          BuildInfo.sourceDirectory.toString(),
          ""
        )
        .replace('\\', '/')
    }
  }

  def check(t: FrameworkTest): Unit = {
    test(t.cls.getSimpleName().withTags(t.tags)) {
      val baos = new ByteArrayOutputStream()
      val out = new PrintStream(baos)
      val logger = new Logger {
        def ansiCodesSupported(): Boolean = false
        def error(x: String): Unit = out.println(x)
        def warn(x: String): Unit = out.println(x)
        def info(x: String): Unit = out.println(x)
        def debug(x: String): Unit = () // ignore debugging output
        def trace(x: Throwable): Unit = out.println(x)
      }
      val framework = new Framework
      val runner = framework.runner(
        t.arguments ++ Array("+l"), // use sbt loggers
        Array(),
        PlatformCompat.getThisClassLoader
      )
      val tasks = runner.tasks(
        Array(
          new TaskDef(
            t.cls.getName(),
            framework.munitFingerprint,
            false,
            Array()
          )
        )
      )
      val events = new StringBuilder()
      val eventHandler = new EventHandler {
        def handle(event: Event): Unit = {
          try {
            events.append(t.onEvent(event))
            val status = event.status().toString().toLowerCase()
            val name = event.fullyQualifiedName()
            events
              .append("==> ")
              .append(status)
              .append(" ")
              .append(name)
            if (event.throwable().isDefined()) {
              events
                .append(" - ")
                .append(exceptionMessage(event.throwable().get()))
            }
            events.append("\n")
          } catch {
            case NonFatal(e) =>
              e.printStackTrace()
              events.append(s"unexpected error: $e")
          }
        }
      }
      implicit val ec = munitExecutionContext
      val elapsedTimePattern =
        Pattern.compile(" \\d+\\.\\d+s$", Pattern.MULTILINE)
      TestingConsole.out = out
      TestingConsole.err = out
      for {
        _ <- tasks.foldLeft(Future.successful(())) { case (base, task) =>
          base.flatMap(_ =>
            PlatformCompat.executeAsync(
              task,
              eventHandler,
              Array(logger)
            )
          )
        }
      } yield {
        val stdout =
          AnsiColors.filterAnsi(baos.toString(StandardCharsets.UTF_8.name()))
        val obtained = AnsiColors.filterAnsi(
          t.format match {
            case SbtFormat =>
              events.toString().replace("\"\"\"", "'''")
            case StdoutFormat =>
              elapsedTimePattern.matcher(stdout).replaceAll(" <elapsed time>")
          }
        )
        assertNoDiff(
          obtained,
          t.expected,
          stdout
        )(t.location)
      }
    }(t.location)
  }
}
