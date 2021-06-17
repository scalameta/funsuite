---
id: file-layout
title: File Layout
---

MUnit provides some utilities to test programs that manipulate files.

You can use `FileLayout` to describe the content of files on disk and either
write them to use them as input for your test or check that the files on disk
are as expected.

Example:

suppose you are testing a CLI tool that can read credentials from a file

```scala mdoc
import java.nio.file.Paths
import java.nio.file.Files

object MyCliTool {
  def main(args: Array[String]): Unit =
    credentialsFromFile(args(0)) match {
      case Some(value) => println("Credentials found!")
      case None        => println("No credentials found :(")
    }

  def credentialsFromFile(configPath: String): Option[String] = {
    val path = Paths.get(configPath)
    if (Files.exists(path))
      Some(new String(Files.readAllBytes(Paths.get(configPath))))
    else
      None
  }
}
```

You can `FileLayout` to write a test for reading the credentials:

```scala mdoc
import munit.FunSuite
import munit.FileLayout

class MyCliSuite extends FunSuite {

  test("credentials-from-file") {
    val authPath = ".myCli/auth"
    val expectedToken = "token=abcdef123456"
    val root = FileLayout.write(
      s"""|/${authPath}
          |${expectedToken}
          |""".stripMargin)
    val actualToken = MyCliTool.credentialsFromFile(root.resolve(authPath).toString)
    assertEquals(actualToken, Some(expectedToken))
  }

}
```

`FileLayout.write` takes a string describing the paths and contents of files on
disk and returns the root of the directory where the files were written (a
temporary directory, by default)

Lines starting with a single `/` are interpreted as file paths, allowing you to
describe multiple files in a single layout string:

```scala
val layout =
 s"""|/a/b/c/D.scala
     |object Test {
     |
     |}
     |
     |/a/b/c/e.json
     |{"any": "thing"}
     |""".stripMargin
```

You can also use `FileLayout.fromString` to parse a layout into a data structure
representing files and contents. This is useful - for example - when writing
tests for code generators that need to check the content of generated files:

```scala
object MyCodeGen {
  def generateFiles(): Path = ???
}

class MyCodeGenSuite extends FunSuite {
  test("generated-files") {
    val layout = """..."""
    val root = MyCodeGen.generateFiles()
    FileLayout.fromString(layout).files.map {
      case (path, expectedContent) =>
        val actualContent = new String(Files.readAllBytes(root.resolve(path)))
        assertNoDiff(actualContent, expectedContent)
    }
  }
}
```
