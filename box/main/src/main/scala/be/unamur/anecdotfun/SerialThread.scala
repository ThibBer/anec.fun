package be.unamur.anecdotfun

import com.fazecast.jSerialComm.SerialPort

import java.io.PipedInputStream
import scala.concurrent.duration.FiniteDuration

enum SerialMode:
  case Standard, VoiceFlow

class SerialThread(portDescriptor: String) extends Thread {
  private val messageBuffer = new StringBuilder()
  private val pipedInputStream = new PipedInputStream()
  private val comPort = SerialPort.getCommPort(portDescriptor)
  private var mode = SerialMode.Standard


  var onConnected: () => Unit = () => {}
  var onReceiveSerialData: String => Unit = _ => {}
  var onReceiveVoiceSerialData: Array[Byte] => Unit = _ => {}

  override def start(): Unit = {
    super.start()
    if(comPort.openPort(1000)) {
      onConnected()
      comPort.setBaudRate(115200)
    } else {
      println(s"Failed to open port $portDescriptor")
    }
  }

  override def run(): Unit = {
    super.run()

    comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)

    val buffer = new Array[Byte](1024)
    var bytesRead = 0

    try {
      while ( {
        bytesRead = comPort.readBytes(buffer, buffer.length)
        bytesRead
      } != -1) {
        mode match
          case SerialMode.Standard =>
            handleStandardMode(buffer, bytesRead)
          case SerialMode.VoiceFlow =>
            handleVoiceFlowMode(buffer, bytesRead)
      }
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      comPort.closePort()
    }
  }

  private def handleStandardMode(buffer: Array[Byte], bytesRead: Int): Unit = {
    val receivedText = new String(buffer, 0, bytesRead)
    messageBuffer.append(receivedText)

    if (receivedText.contains("\n")) {
      val lines = messageBuffer.toString().split("\n", 2)
      onReceiveSerialData(lines(0).trim())
      messageBuffer.clear()
      if (lines.length > 1) {
        messageBuffer.append(lines(1))
      }
    }
  }

  private def handleVoiceFlowMode(buffer: Array[Byte], bytesRead: Int): Unit = {
    if (bytesRead <= 0) {
      return
    }
    onReceiveVoiceSerialData(buffer.take(bytesRead))
  }

  def send(message: String): Unit = {
    println(s"Send to serial : $message")
    val msgBytes = (message ++ "\n").getBytes
    val nbBytesWrote = comPort.writeBytes(msgBytes, msgBytes.length)
    println(s"Nb bytes wrote : $nbBytesWrote")
  }

  def startVoiceFlow(time: FiniteDuration): Unit = {
    send(s"${MessageKey.VoiceFlowStart}=${time.toSeconds + 1}s")
    mode = SerialMode.VoiceFlow
  }

  def stopVoiceFlow(): Unit = {
    mode = SerialMode.Standard
  }
}
