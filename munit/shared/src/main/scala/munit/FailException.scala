package munit

class FailException(
    val message: String,
    val cause: Throwable,
    val isStackTracesEnabled: Boolean,
    val location: Location
) extends AssertionError(message, cause)
    with FailExceptionLike[FailException] {
  def this(message: String, location: Location) =
    this(message, null, isStackTracesEnabled = true, location)
  def this(message: String, cause: Throwable, location: Location) = this(
    message,
    cause,
    isStackTracesEnabled = true,
    location
  )
  def withMessage(newMessage: String): FailException =
    copy(message = newMessage)

  private[munit] def copy(
      message: String = this.message,
      cause: Throwable = this.cause,
      isStackTracesEnabled: Boolean = this.isStackTracesEnabled,
      location: Location = this.location
  ): FailException =
    new FailException(message, cause, isStackTracesEnabled, location)
  override def fillInStackTrace(): Throwable = {
    val result = super.fillInStackTrace()
    if (!isStackTracesEnabled) {
      result.setStackTrace(result.getStackTrace().slice(0, 1))
    }
    result
  }

}
