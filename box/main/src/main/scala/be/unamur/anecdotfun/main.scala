package be.unamur.anecdotfun

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{DateTime, StatusCode}
import akka.pattern.after
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import be.unamur.anecdotfun.CommandResponseJsonProtocol.commandResponseFormat
import be.unamur.anecdotfun.GameState.{START, STOP}
import com.typesafe.config.ConfigFactory
import spray.json.*

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.concurrent.duration.DurationInt
import scala.sys.process.Process

val config = ConfigFactory.load()
implicit val system: ActorSystem = ActorSystem("box-anecdotfun", config)
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

var uniqueId = readSavedUniqueId()

val uniqueIdValidDuration = config.getString("akka.game.box.unique-id-valid-duration").toInt // in minutes
val boxId = Option(System.getenv("BOX_ID")).getOrElse(config.getString("akka.game.box.id")).toInt
val baseUrl = Option(System.getenv("BASE_URL")).getOrElse(config.getString("akka.game.server.base-url")) + boxId
var webSocketClient: WebSocketClient = null
val comPort = Option(System.getenv("COMPORT")).getOrElse(config.getString("akka.game.arduino.com-port"))
val serial = SerialThread(comPort)
val mic = Microphone(serial)
var exponentialRetryCount = 0
val exponentialRetryMaxCount = 5

object Main {
  def main(args: Array[String]): Unit = {
    serial.onReceiveSerialData = onReceiveSerialData
    serial.onConnected = onSerialConnected()

    serial.start()
  }

  private val serialMessageAction: Map[String, String => Unit] = Map(
    MessageKey.SetGameState -> onRequestChangeGameState,
    MessageKey.GameMode -> onRequestChangeGameMode,
    MessageKey.HandDetected -> onHandDetected,
    MessageKey.RequestShutdown -> onRequestShutdown,
  )

  private def onSerialConnected(): Unit = {
    println(s"Port opened, reading serial on $comPort")

    println(s"Try to connect to websocket ...")
    connectToWebsocket()
  }

  private def onWebSocketConnectionClosed(): Unit = {
    println(s"WebSocket connection closed ${dateTimeString()}")
    websocketConnectionExponentialRetry()
  }

  private def onConnectionFailed(status: StatusCode): Unit = {
    println(s"WebSocket connection failed: $status")
    websocketConnectionExponentialRetry()
  }

  private def onConnectedToWebSocket(statusCode: StatusCode): Unit = {
    exponentialRetryCount = 0

    println(s"WebSocket connection established ${dateTimeString()}")
    connectBoxToServer()
  }

  private def connectToWebsocket(): Unit = {
    val websocketUrl = if (uniqueId.isEmpty) baseUrl else baseUrl + "?uniqueId=" + uniqueId
    webSocketClient = WebSocketClient(websocketUrl)

    webSocketClient.onReceiveMessage = onReceiveWebSocketMessage
    webSocketClient.onConnectionEstablished = onConnectedToWebSocket
    webSocketClient.onConnectionFailed = onConnectionFailed
    webSocketClient.onConnectionClosed = onWebSocketConnectionClosed
  }

  private def websocketConnectionExponentialRetry(): Unit = {
    if(exponentialRetryCount >= exponentialRetryMaxCount){
      println(s"Cancel websocket exponential retry after $exponentialRetryCount try")
      exponentialRetryCount = 0
      return
    }

    after(math.pow(2, exponentialRetryCount).seconds, using = system.scheduler) {
      Future{
        exponentialRetryCount = exponentialRetryCount + 1
        println(s"Websocket exponential retry $exponentialRetryCount/$exponentialRetryMaxCount")
        connectToWebsocket()
      }
    }
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
            if(commandResponse.uniqueId != uniqueId){
              uniqueId = commandResponse.uniqueId
              saveUniqueId(uniqueId)
            }
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
        case CommandType.ANECDOTE_TELLER =>
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
      case None =>
        println("Error start listening")
        webSocketClient.send(JsObject(
          "boxId" -> JsNumber(boxId),
          "uniqueId" -> JsString(uniqueId),
          "commandType" -> JsString(CommandType.VOICE_FLOW),
          "payload" -> JsNull
        ))
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

def readSavedUniqueId(): String = {
  val path = os.Path(System.getProperty("user.home") + "/uniqueId.iot")
  if (!os.exists(path)){
    println("File not exists")
    return ""
  }

  try {
    println(s"Read uniqueId file : $path")
    val lines = os.read.lines(path)
    val uniqueId = os.read.lines(path).apply(0)
    val timestamp = os.read.lines(path).apply(1)
    val dateTime = DateTime.fromIsoDateTimeString(timestamp)

    println(s"UniqueId : $uniqueId")
    println(s"timestamp : $timestamp")
    println(s"DateTime : $dateTime")

    dateTime match {
      case None =>
        print("Invalid datetime")
        ""
      case Some(dt) =>
        if((DateTime.now.clicks - dt.clicks) / 60000 > uniqueIdValidDuration)
          print("UniqueId expired")
          ""
        else
          print("UniqueId is valid")
          uniqueId // local id is valid 10 min
    }
  } catch {
    case e: NoSuchElementException =>
      println(s"UniqueId file exists but empty : $path")
      os.remove(path)
      ""
  }
}

def saveUniqueId(id: String): Unit = {
  val path = os.Path(System.getProperty("user.home") + "/uniqueId.iot")
  println(s"Save uniqueId file : $path")
  os.write.over(path, s"$id\n${DateTime.now}")
}
