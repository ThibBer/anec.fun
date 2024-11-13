package be.unamur.anecdotfun

import com.fazecast.jSerialComm.SerialPort

import java.io.PipedInputStream

class SerialThread(port: String) extends Thread {
  private val messageBuffer = new StringBuilder()
  private val pipedInputStream = new PipedInputStream()
  private val comPort = SerialPort.getCommPort(port)
  comPort.openPort(1000)
  comPort.setBaudRate(115200)
  println(s"Port opened, reading serial on $port")

  var onReceiveSerialData: String => Unit = _ => {}

  override def run(): Unit = {
    super.run()

    comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)

    val stream = comPort.getInputStream

    val buffer = new Array[Byte](1024)
    var bytesRead = 0

    try {
      while ({bytesRead = stream.read(buffer); bytesRead} != -1) {
        val receivedText = new String(buffer, 0, bytesRead)
        messageBuffer.append(receivedText)

        if (receivedText.contains("\n")) {
          onReceiveSerialData(messageBuffer.toString().trim())
          messageBuffer.clear()
        }
      }
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      stream.close()
    }
  }

  def send(message: String): Unit = {
    println("Send message to serial : " + message)
    comPort.getOutputStream.write(message.getBytes)
    comPort.getOutputStream.flush()
  }
}
