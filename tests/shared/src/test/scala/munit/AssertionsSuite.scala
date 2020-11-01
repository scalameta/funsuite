package munit

import munit.internal.console.Printers

class AssertionsSuite extends BaseSuite {
  def check(
      name: TestOptions,
      cond: => Boolean,
      expected: String
  )(implicit loc: Location): Unit =
    test(name.tag(NoDotty)) {
      val (_, clues) = munitCaptureClues(cond)
      assertNoDiff(Printers.print(clues), expected)
    }

  val a = 42
  val b = 43L
  val c: List[Int] = List(41)
  check(
    "basic",
    clue(a) > clue(b),
    """|Clues {
       |  a: Int = 42
       |  b: Long = 43
       |}
       |""".stripMargin
  )

  check(
    "expr",
    clue(a) > clue(c.head),
    """|Clues {
       |  a: Int = 42
       |  c.head: Int = 41
       |}
       |""".stripMargin
  )

  check(
    "subexpr",
    clue(a) > clue(c).head,
    """|Clues {
       |  a: Int = 42
       |  c: List[Int] = List(
       |    41
       |  )
       |}
       |""".stripMargin
  )

  test("subtype".tag(NoDotty)) {
    assertEquals(Option(1), Some(1))
    assertEquals(Some(1), Option(1))
    assertEquals(Option(1), Option(1))
  }

  test("false-negative") {
    assertNoDiff(
      compileErrors("assertEquals(List(1), Vector(1))"),
      if (isDotty)
        """|error:
           |Can't compare these two types:
           |  First type:  List[Int]
           |  Second type: Vector[Int]
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[List[Int], Vector[Int]]
           |  Alternative 2: upcast either type into `Any` or a shared supertype.
           |I found:
           |
           |    munit.Compare.compareSupertypeWithSubtype[A, B](
           |      /* missing */summon[Vector[Int] <:< List[Int]]
           |    )
           |
           |But no implicit values were found that match type Vector[Int] <:< List[Int].
           |
           |The following import might make progress towards fixing the problem:
           |
           |  import munit.CustomCompare.fromCustomEquality
           |
           |assertEquals(List(1), Vector(1))
           |                               ^
           |""".stripMargin
      else
        """|error:
           |Can't compare these two types:
           |  First type:  List[Int]
           |  Second type: scala.collection.immutable.Vector[Int]
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[List[Int], scala.collection.immutable.Vector[Int]]
           |  Alternative 2: upcast either type into `Any` or a shared supertype
           |assertEquals(List(1), Vector(1))
           |            ^
           |""".stripMargin
    )
  }

  test("unrelated") {
    class A {
      override def equals(x: Any): Boolean = true
    }
    class B {
      override def equals(x: Any): Boolean = true
    }
    assertNoDiff(
      compileErrors("assertEquals(new A, new B)"),
      if (isDotty)
        """|error:
           |Can't compare these two types:
           |  First type:  A
           |  Second type: B
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[A, B]
           |  Alternative 2: upcast either type into `Any` or a shared supertype.
           |I found:
           |
           |    munit.Compare.compareSupertypeWithSubtype[A, B](/* missing */summon[B <:< A])
           |
           |But no implicit values were found that match type B <:< A.
           |
           |The following import might make progress towards fixing the problem:
           |
           |  import munit.CustomCompare.fromCustomEquality
           |
           |assertEquals(new A, new B)
           |                         ^
           |""".stripMargin
      else
        """|error:
           |Can't compare these two types:
           |  First type:  A
           |  Second type: B
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[A, B]
           |  Alternative 2: upcast either type into `Any` or a shared supertype
           |assertEquals(new A, new B)
           |            ^
           |""".stripMargin
    )
  }

  test("chat-int-nok") {
    assertNoDiff(
      compileErrors("assertEquals('a', 'a'.toInt)"),
      if (isDotty)
        """|error:
           |Can't compare these two types:
           |  First type:  Char
           |  Second type: Int
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[Char, Int]
           |  Alternative 2: upcast either type into `Any` or a shared supertype.
           |I found:
           |
           |    munit.Compare.compareSupertypeWithSubtype[A, B](
           |      /* missing */summon[Int <:< Char]
           |    )
           |
           |But no implicit values were found that match type Int <:< Char.
           |
           |The following import might make progress towards fixing the problem:
           |
           |  import munit.CustomCompare.fromCustomEquality
           |
           |assertEquals('a', 'a'.toInt)
           |                           ^
           |""".stripMargin
      else
        """|error:
           |Can't compare these two types:
           |  First type:  Char
           |  Second type: Int
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[Char, Int]
           |  Alternative 2: upcast either type into `Any` or a shared supertype
           |assertEquals('a', 'a'.toInt)
           |            ^
           |""".stripMargin
    )
  }

  test("some-none-nokj") {
    assertNoDiff(
      compileErrors("assertEquals(None, Some(1))"),
      if (isDotty)
        """|error:
           |Can't compare these two types:
           |  First type:  None.type
           |  Second type: Some[Int]
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[None.type, Some[Int]]
           |  Alternative 2: upcast either type into `Any` or a shared supertype.
           |I found:
           |
           |    munit.Compare.compareSupertypeWithSubtype[A, B](
           |      /* missing */summon[Some[Int] <:< None.type]
           |    )
           |
           |But no implicit values were found that match type Some[Int] <:< None.type.
           |
           |The following import might make progress towards fixing the problem:
           |
           |  import munit.CustomCompare.fromCustomEquality
           |
           |assertEquals(None, Some(1))
           |                          ^
           |""".stripMargin
      else
        """|error:
           |Can't compare these two types:
           |  First type:  None.type
           |  Second type: Some[Int]
           |Possible ways to fix this error:
           |  Alternative 1: provide an implicit instance for Compare[None.type, Some[Int]]
           |  Alternative 2: upcast either type into `Any` or a shared supertype
           |assertEquals(None, Some(1))
           |            ^
           |""".stripMargin
    )
  }

}
