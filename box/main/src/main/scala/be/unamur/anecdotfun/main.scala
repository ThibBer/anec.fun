package be.unamur.anecdotfun

import scala.concurrent.duration.DurationInt
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.{DateTime, StatusCode}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import be.unamur.anecdotfun.GameState.{START, STOP}
import com.typesafe.config.ConfigFactory
import spray.json.*
import be.unamur.anecdotfun.CommandResponseJsonProtocol.commandResponseFormat

val config = ConfigFactory.load()
implicit val system: ActorSystem = ActorSystem("box-anecdotfun", config)
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

val boxId = config.getInt("akka.game.box.id")
var webSocketClient = WebSocketClient(config.getString("akka.game.server.base-url") + boxId)
val serial = SerialThread(config.getString("akka.game.arduino.com-port"))
val mic = Microphone(serial)
var uniqueId = ""


object Main {
  def main(args: Array[String]): Unit = {
    serial.onReceiveSerialData = onReceiveSerialData

    webSocketClient.onReceiveMessage = onReceiveWebSocketMessage
    webSocketClient.onConnectionEstablished = onConnectedToWebSocket
    webSocketClient.onConnectionFailed = (status: StatusCode) => println(s"WebSocket connection failed: $status")
    webSocketClient.onConnectionClosed = () => println(s"WebSocket connection closed ${dateTimeString()}")
  }

  private val serialMessageAction: Map[String, String => Unit] = Map(
    MessageKey.SetGameState -> onRequestChangeGameState,
    MessageKey.Mode -> onRequestChangeMode,
    MessageKey.HandDetected -> onHandDetected,
  )

  private def onConnectedToWebSocket(statusCode: StatusCode): Unit = {
    println(s"WebSocket connection established ${dateTimeString()}")

    serial.start()
    connectBoxToServer()
  }

  private def onReceiveSerialData(serialInput: String): Unit = {
    println("Received from serial : " + serialInput)

    if (serialInput.toLowerCase().contains("debug")) { // ignore debug message sent from serial
      return
    }

    val parts = serialInput.split('=')

    parts match {
      case Array(name, value) =>
        println(s"Key: $name - $value")

        serialMessageAction.get(name) match {
          case Some(callback) => callback(value)
          case None => println(s"Undefined message key ($name)")
        }
      case _ =>
        println("Invalid input format received from serial")
    }
  }

  private def connectBoxToServer(): Unit = {
    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "commandType" -> JsString(CommandType.CONNECT_BOX),
      "uniqueId" -> JsString(uniqueId)
    ))
  }

  private def onReceiveWebSocketMessage(message: String): Unit = {
    println("Receive websocket message : " + message)

    try {
      val commandResponse = message.parseJson.convertTo[CommandResponse]
      val isCommandSuccessful = commandResponse.status == CommandStatus.SUCCESS

      if (!isCommandSuccessful) {
        println("Failed command : " + commandResponse.commandType)
      }

      commandResponse.commandType match {
        case CommandType.CONNECTION =>
          if (isCommandSuccessful) {
            uniqueId = commandResponse.uniqueId
          }
        case CommandType.START_GAME =>
          if (isCommandSuccessful) {
            onGameStateChanged("STARTED")
          } else {
            onGameStateChanged("STOPPED")
          }
        case CommandType.STOP_GAME =>
          if (isCommandSuccessful) {
            onGameStateChanged("STOPPED")
          }
        case CommandType.STICK_EXPLODED =>
          if (isCommandSuccessful) {
            onStickExploded()
          }
        case _ => println(s"Unmanaged response command type (${commandResponse.commandType})")
      }
    } catch {
      case e: DeserializationException => println(e)
      case e => e.printStackTrace()
    }
  }

  private def onGameStateChanged(state: String): Unit = {
    serial.send(MessageKey.GameStateChanged + '=' + state)
  }

  private def onRequestChangeGameState(value: String): Unit = {
    val state = GameState.valueOf(value)

    var command = ""

    state match {
      case START => command = CommandType.START_GAME
      case STOP => command = CommandType.STOP_GAME
      case _ =>
    }

    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString(command)
    ))
  }

  private def onRequestChangeMode(value: String): Unit = {
    println("The box does not currently support mode switching")
  }

  private def onHandDetected(value: String): Unit = {
    println("On hand detected")

    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString("StartRoundCommand")
    ))
  }

  private def onStickExploded(): Unit = {
    // todo: make anecdote duration configurable
    val duration = 10.seconds
    val sink = Sink.foreach[ByteString](data => {
      webSocketClient.send(JsObject(
        "boxId" -> JsNumber(boxId),
        "uniqueId" -> JsString(uniqueId),
        "commandType" -> JsString(CommandType.VOICE_FLOW),
        "payload" -> JsArray(data.map(a => JsNumber(a)).toVector)
      ))
    })
    mic.startListening(sink, duration) match {
      case None => println("Error start listening")
      case Some(graph) =>
        val runningGraph = graph.run()
        val cancellable: Cancellable = system.scheduler.scheduleOnce(duration) {
          println(s"Stopping graph after $duration seconds")
          mic.close()
          webSocketClient.send(JsObject(
            "boxId" -> JsNumber(boxId),
            "uniqueId" -> JsString(uniqueId),
            "commandType" -> JsString(CommandType.VOICE_FLOW),
            "payload" -> JsNull
          ))
        }
    }
    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString("VoiceFlow"),
      "payload" -> JsArray(),
    ))
  }

  private def dateTimeString(): String = {
    val dateTime = DateTime.now
    s"${dateTime.day}/${dateTime.month}/${dateTime.year} ${dateTime.hour}:${dateTime.minute}:${dateTime.second}"
  }

  private def twoDigitsNumber(data: Int): String = if (data <= 9) "0" + data else data.toString
}
