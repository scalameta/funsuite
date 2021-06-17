package munit

import java.nio.file.Files
import java.nio.file.Paths

class FileLayoutSuite extends FunSuite {

  test("roundtrip") {
    val layout = """|/a/A.scala
                    |object A {}
                    |/b/b/B.scala
                    |trait B {}
                    |/C.scala
                    |class C
                    |""".stripMargin
    val root = FileLayout.write(layout)
    FileLayout.fromString(layout).files.map {
      case (path, expectedContent) =>
        val absolutePath = root.resolve(Paths.get(path))
        val actualContent = new String(Files.readAllBytes(absolutePath))
        assertNoDiff(actualContent, expectedContent)
    }
  }

  test("malformed") {
    intercept[IllegalArgumentException] {
      FileLayout.fromString("object A {}")
    }
  }

}
