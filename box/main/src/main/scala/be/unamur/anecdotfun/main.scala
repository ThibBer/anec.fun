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
import scala.sys.process.Process

val config = ConfigFactory.load()
implicit val system: ActorSystem = ActorSystem("box-anecdotfun", config)
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

val boxId = Option(System.getenv(
  "BOX_ID")).getOrElse(config.getString("akka.game.box.id")).toInt
var webSocketClient = WebSocketClient(Option(System.getenv(
  "BASE_URL")).getOrElse(config.getString("akka.game.server.base-url")) + boxId)
val serial = SerialThread(Option(System.getenv(
  "COMPORT")).getOrElse(config.getString("akka.game.arduino.com-port")))
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
    MessageKey.GameMode -> onRequestChangeGameMode,
    MessageKey.HandDetected -> onHandDetected,
    MessageKey.RequestShutdown -> onRequestShutdown,
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
        case CommandType.STATUS =>
          if (isCommandSuccessful) {
            commandResponse.message match {
              case Some(message) => onGameStateChanged(message)
              case None =>
            }
          }
        case CommandType.START_GAME =>
          if (!isCommandSuccessful) {
            onGameStateChanged("ERROR")
          }
        case CommandType.STOP_GAME =>
          if (isCommandSuccessful) {
            onGameStateChanged("STOPPED")
          }
        case CommandType.STICK_EXPLODED =>
          if (isCommandSuccessful) {
            onStickExploded()
          }
        case CommandType.GAME_MODE_CHANGED =>
          if (isCommandSuccessful) {
            commandResponse.message match {
              case Some(message) => onGameModeChanged(message)
              case None =>
            }
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

  private def onRequestChangeGameMode(value: String): Unit = {
    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString(CommandType.SET_GAME_MODE),
      "gameMode" -> JsString(value)
    ))
  }

  private def onHandDetected(value: String): Unit = {
    println("On hand detected")

    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString("StartRoundCommand")
    ))
  }

  private def onRequestShutdown(value: String): Unit = {
    val operatingSystem = System.getProperty("os.name")
    println(operatingSystem)

    if (operatingSystem != "Linux") {
      println(s"Can't shutdown $operatingSystem. only linux works")
      return
    }

    webSocketClient.send(JsObject(
      "boxId" -> JsNumber(boxId),
      "uniqueId" -> JsString(uniqueId),
      "commandType" -> JsString(CommandType.STOP_GAME)
    ))

    Process("shutdown -h now")
    System.exit(0)
  }

  private def onStickExploded(): Unit = {
    // todo: make anecdote max duration configurable
    val duration = 5.minutes
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
      case Some(completionFuture) =>
        completionFuture.onComplete { _ =>
          println("Flow completed early, cancelling scheduled task")
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

  private def onGameModeChanged(state: String): Unit = {
    serial.send(MessageKey.GameModeChanged + '=' + state)
  }

  private def dateTimeString(): String = {
    val dateTime = DateTime.now
    s"${dateTime.day}/${dateTime.month}/${dateTime.year} ${dateTime.hour}:${dateTime.minute}:${dateTime.second}"
  }

  private def twoDigitsNumber(data: Int): String = if (data <= 9) "0" + data else data.toString
}