package munit.internal

import munit.Clue
import munit.Compare
import munit.Location
import scala.quoted._

object MacroCompat {

  trait LocationMacro {
    inline implicit def generate: Location = ${ locationImpl() }
  }

  def locationImpl()(using qctx: QuoteContext): Expr[Location] = {
    import qctx.tasty.{_, given _}
    val path = rootPosition.sourceFile.jpath.toString
    val startLine = rootPosition.startLine + 1
    '{ new Location(${Expr(path)}, ${Expr(startLine)}) }
  }

  trait ClueMacro {
    inline implicit def generate[T](value: T): Clue[T] = ${ clueImpl('value) }
  }

  def clueImpl[T:Type](value: Expr[T])(using qctx: QuoteContext): Expr[Clue[T]] = {
    import qctx.tasty.{_, given _}
    val source = value.unseal.pos.sourceCode
    val valueType = implicitly[scala.quoted.Type[T]].show
    '{ new Clue(${Expr(source)}, $value, ${Expr(valueType)}) }
  }

  trait CompileErrorMacro {
    inline def compileErrors(inline code: String): String = {
      val errors = scala.compiletime.testing.typeCheckErrors(code)
      errors.map { error =>
        val indent = " " * (error.column - 1)
        val trimMessage = error.message.linesIterator.map { line =>
          if (line.matches(" +")) ""
          else line
        }.mkString("\n")
        val separator = if (error.message.contains('\n')) "\n" else " "
        s"error:${separator}${trimMessage}\n${error.lineContent}\n${indent}^"
      }.mkString("\n")
    }
  }

}
