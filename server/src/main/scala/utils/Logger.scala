package utils

object Logger {
  def log(message: String): Unit = {
    println(s"[LOG] $message")
  }
}
