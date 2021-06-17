package java.nio.file

import java.{util => ju}
import java.nio.charset.StandardCharsets

import munit.internal.JSFs
import munit.internal.JSIO
import munit.internal.JSPath
import munit.internal.JSOS

import scala.collection.JavaConverters._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array

object Files {
  def readAllLines(path: Path): ju.List[String] = {
    val bytes = readAllBytes(path)
    val text = new String(bytes, StandardCharsets.UTF_8)
    text.linesIterator.toSeq.asJava
  }

  def readAllBytes(path: Path): Array[Byte] = JSIO.inNode {
    val jsArray = JSFs.readFileSync(path.toString)
    val len = jsArray.length
    val result = new Array[Byte](len)
    var curr = 0
    while (curr < len) {
      result(curr) = jsArray(curr).toByte
      curr += 1
    }
    result
  }

  def createDirectories(dir: Path): Path = JSIO.inNode {
    JSFs.mkdirSync(dir.toString(), js.Dynamic.literal(recursive = true))
    dir
  }

  def createTempDirectory(prefix: String): Path = JSIO.inNode {
    Paths.get(JSFs.mkdtempSync(JSPath.join(JSOS.tmpdir(), prefix)))
  }

  def exists(path: Path): Boolean = JSIO.inNode {
    JSIO.exists(path.toString)
  }

  def deleteIfExists(path: Path): Boolean = JSIO.inNode {
    if (JSIO.exists(path.toString)) {
      JSFs.unlinkSync(path.toString)
      true
    } else {
      false
    }
  }

  def write(path: Path, bytes: Array[Byte], options: OpenOption*): Path =
    JSIO.inNode {
      val len = bytes.length
      val data = new js.Array[Short]()
      var curr = 0
      while (curr < len) {
        data(curr) = bytes(curr).toShort
        curr += 1
      }
      JSFs.writeFileSync(
        path.toString(),
        Uint8Array.from(data),
        js.Dynamic.literal(flag = openOptionsToFlag(options))
      )
      path
    }

  private def openOptionsToFlag(
      options: Seq[OpenOption]
  ): js.UndefOr[String] = {
    import StandardOpenOption._
    val combinations = List(
      List(WRITE, READ, CREATE) -> "w+",
      List(WRITE, READ, CREATE_NEW) -> "wx+",
      List(WRITE, CREATE) -> "w",
      List(WRITE, CREATE_NEW) -> "wx",
      List(APPEND, READ, CREATE) -> "a+",
      List(APPEND, READ, CREATE_NEW) -> "ax+",
      List(APPEND, CREATE) -> "a",
      List(APPEND, CREATE_NEW) -> "ax",
      List(WRITE, READ) -> "r+",
      List(READ) -> "r"
    )
    val optionsSet = options.toSet
    combinations.collectFirst {
      case (comb, flag) if comb.toSet.subsetOf(optionsSet) => flag
    }.orUndefined
  }
}
