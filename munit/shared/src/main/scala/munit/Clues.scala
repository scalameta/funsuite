package munit

import munit.internal.console.Printers

class Clues(val values: List[Clue[_]]) {
  override def toString(): String = Printers.print(this)
}
object Clues {
  def empty: Clues = new Clues(List())
  def fromValue[T](value: T): Clues = new Clues(List(Clue.fromValue(value)))
}
