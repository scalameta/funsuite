---
id: assertions
title: Writing assertions
---

MUnit provides a few ways to fail a test given a condition.

```scala mdoc:invisible
object Tests extends munit.FunSuite {
  override val munitAnsiColors = false
}
import Tests._
```

## `assert()`

Use `assert()` to fail a test if a boolean condition does not hold true. For
example, assume we have two values:

```scala mdoc
val a = 1
val b = 2
```

In the most basic case when no hints are provided, the error message is
"assertion failed" when the condition is false.

```scala mdoc:crash
assert(a > b)
```

Include an optional message to explain why the assertion failed.

```scala mdoc:crash
assert(a > b, "a was smaller than b")
```

Use `clue()` to include optional clues in the boolean condition based on values
in the expression.

```scala mdoc:crash
assert(clue(a) > clue(b))
```

> Note, the `-Yrangepos` compiler option is required for `clue()` to work
> correctly. When `-Yrangepos` is not enabled you may see output like this
> instead:
>
> ```
> // Clues {
> //   : Int = 1
> //   : Int = 2
> // }
> ```
>
> To fix this problem in sbt, add the following line to your settings:
>
> ```diff
>  // build.sbt
>  lazy val myProject = project
>    .settings(
> +    scalacOptions += "-Yrangepos"
>    )
> ```

Clues can wrap more complicated expressions.

```scala mdoc:crash
assert(clue(List(a).head) > clue(b))
```

## `assertEquals()`

Use `assertEquals()` to assert that two values are the same.

```scala mdoc:crash
assertEquals(a, b)
```

The error message automatically produces a diff on assertion failure.

```scala mdoc
case class Library(name: String, awesome: Boolean, versions: Range = 0.to(1))
val munitLibrary = Library("MUnit", true)
val mdocLibrary = Library("MDoc", true)
```

```scala mdoc:crash
assertEquals(munitLibrary, mdocLibrary)
```

Diffs make it easy to track down differences even in large data structures.

```scala mdoc:crash
assertEquals(
  Map(1 -> List(1.to(3))),
  Map(1 -> List(1.to(4)))
)
```

Comparing two values of different types is a compile error.

```scala mdoc:fail
assertEquals(Option("message"), "message")
```

It's a compile error even if the comparison is true at runtime.

```scala mdoc:fail
assertEquals(List(1), Vector(1))
```

```scala mdoc:fail
assertEquals('a', 'a'.toInt)
```

It's OK to compare two types as long as one argument is a subtype of the other
type.

```scala mdoc
assertEquals(Option(1), Some(1)) // OK
assertEquals(Some(1), Option(1)) // OK
```

Use `assertEquals[Any, Any]` if you think it's OK to compare the two types at
runtime.

```scala mdoc
val right1: Either[String      , Int] = Right(42)
val right2: Either[List[String], Int] = Right(42)
```

```scala mdoc
assertEquals[Any, Any](right1, right2)
```

## `assertNotEquals()`

Use `assertNotEqual()` to assert that two values are not the same.

```scala mdoc:crash
assertNotEquals(a, a)
```

The assertion does not fail when both values are different.

```scala mdoc
// OK
assertNotEquals(a, b)
```

## `assertNoDiff()`

Use `assertNoDiff()` to compare two multiline strings.

```scala mdoc
val obtainedString = "val x = 41\nval y = 43\nval z = 43"
val expectedString = "val x = 41\nval y = 42\nval z = 43"
```

```scala mdoc:crash
assertNoDiff(obtainedString, expectedString)
```

The difference between `assertNoDiff()` and `assertEquals()` is that
`assertEquals()` fails according to the `==` method while `assertNoDiff()`
ignores non-visible differences such as trailing/leading whitespace,
Windows/Unix newlines and ANSI color codes. The "=> Obtained" section of
`assertNoDiff()` error messages also include copy-paste friendly syntax using
`.stripMargin`.

## `intercept()`

Use `intercept()` when you expect a particular exception to be thrown by the
test code (i.e. the test succeeds if the given exception is thrown).

```scala mdoc:crash
intercept[java.lang.IllegalArgumentException]{
   // code expected to throw exception here
}
```

## `interceptMessage()`

Like `intercept()` except additionally asserts that the thrown exception has a
specific error message.

```scala mdoc:crash
interceptMessage[java.lang.IllegalArgumentException]("argument type mismatch"){
   // code expected to throw exception here
}
```

## `fail()`

Use `fail()` to make the test case fail immediately.

```scala mdoc:crash
fail("test failed")
```

Use `clues()` to include optional context why the test failed.

```scala mdoc:crash
fail("test failed", clues(a + b))
```

## `compileErrors()`

Use `compileErrors()` to assert that an example code snippet fails with a
specific compile-time error message.

```scala mdoc
assertNoDiff(
  compileErrors("Set(2, 1).sorted"),
     """|error: value sorted is not a member of scala.collection.immutable.Set[Int]
        |Set(2, 1).sorted
        |          ^
        |""".stripMargin
)
```

The argument to `compileErrors` must be a string literal. It's not possible to
pass in more complicated expressions such as variables or string interpolators.

```scala mdoc:fail
val code = """val x: String = 2"""
compileErrors(code)
compileErrors(s"/* code */ $code")
```

Inline the `code` variable to fix the compile error.

```scala mdoc
compileErrors("val x: String = 2")
```
