package munit

trait CustomCompare[A, B] {
  def isEqual(a: A, b: B): Boolean
}

object CustomCompare {
  implicit val optionEquality: CustomCompare[Some[Int], Option[Int]] =
    new CustomCompare[Some[Int], Option[Int]] {
      def isEqual(a: Some[Int], b: Option[Int]): Boolean = {
        if (a.contains(42)) sys.error("boom")
        else a == b
      }
    }
  implicit def fromCustomEquality[A, B](implicit
      my: CustomCompare[A, B]
  ): Compare[A, B] = {
    new Compare[A, B] {
      def isEqual(a: A, b: B) = my.isEqual(a, b)
    }
  }
}
