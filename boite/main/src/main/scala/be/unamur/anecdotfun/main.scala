package be.unamur.anecdotfun

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.ws.*
import akka.stream.scaladsl.*
import com.fazecast.jSerialComm.SerialPort

import java.io.PipedInputStream
import scala.concurrent.Future


implicit val system: ActorSystem = ActorSystem()
import system.dispatcher

val port = "COM5"
val webSocketAddress = "ws://localhost:8080/ws-echo"

val incoming: Sink[Message, Future[Done]] =
  Sink.foreach[Message] {
    case message: TextMessage.Strict =>
      println(s"Response from WebSocket server : ${message.text}")
    case _ =>
    // ignore other message types
  }

@main
def main(): Unit = {
  val ports = SerialPort.getCommPorts
  val comPort = SerialPort.getCommPort(port)

  comPort.openPort(1000)
  comPort.setBaudRate(115200)

  println(s"Port opened, reading serial on $port")

  val messageBuffer = new StringBuilder()
  val pipedInputStream = new PipedInputStream()

  // Thread to read from serial port and print it
  val readerThread = new Thread(() => {
    comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)

    val stream = comPort.getInputStream

    val buffer = new Array[Byte](1024)
    var bytesRead = 0

    try {
      while ({
        bytesRead = stream.read(buffer); bytesRead
      } != -1) {
        val receivedText = new String(buffer, 0, bytesRead)
        messageBuffer.append(receivedText)

        if (receivedText.contains("\n")) {
          processSerialInput(messageBuffer.toString().trim())
          messageBuffer.clear()
        }
      }
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      stream.close()
    }
  })

  readerThread.start()
}

def processSerialInput(serialInput: String): Unit = {
  val parts = serialInput.split('=')

  parts match {
    case Array(name, value) =>
      println(s"Key: $name - $value")

      val outgoing = Source.single(TextMessage(serialInput))

      val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(webSocketAddress))
      val (upgradeResponse, closed) = outgoing
        .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .run()

      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      val connected = upgradeResponse.flatMap { upgrade =>
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          Future.successful(Done)
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }

      closed.foreach(_ => println("closed"))
    case _ =>
      println("Invalid input")
  }
}