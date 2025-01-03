package be.unamur.anecdotfun

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.{DateTime, StatusCode}

import java.util.Base64
import akka.pattern.after
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import be.unamur.anecdotfun.CommandResponseJsonProtocol.commandResponseFormat
import be.unamur.anecdotfun.GameState.{IDLE, START, STOP}
import be.unamur.anecdotfun.WebSocketState.{CONNECTED, DISCONNECTED}
import com.typesafe.config.ConfigFactory
import spray.json.*

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
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
var webSocketState = DISCONNECTED
var heartbeatTimer: Cancellable = null

object Main {
  def main(args: Array[String]): Unit = {
    serial.onReceiveSerialData = onReceiveSerialData
    serial.onConnected = onSerialConnected

    serial.start()
  }

  private val serialMessageAction: Map[String, String => Unit] = Map(
    MessageKey.InitFinished -> onArduinoInitFinished,
    MessageKey.SetGameState -> onRequestChangeGameState,
    MessageKey.GameMode -> onRequestChangeGameMode,
    MessageKey.HandDetected -> onHandDetected,
    MessageKey.RequestShutdown -> onRequestShutdown,
    MessageKey.RequestWsConn -> onRequestWebsocketConnecting,
  )

  private def onSerialConnected(): Unit = {
    println(s"Port opened, reading serial on $comPort")

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      if(webSocketState == CONNECTED){
        serial.send("WebSocketStateChanged=CLOSED")
      }

      webSocketState = DISCONNECTED
    }))
  }

  private def onWebSocketConnectionClosed(): Unit = {
    println(s"WebSocket connection closed ${dateTimeString()}")

    if(webSocketState == CONNECTED){
      serial.send("WebSocketStateChanged=CLOSED")
      webSocketState = DISCONNECTED
    }

    if(heartbeatTimer != null){
      heartbeatTimer.cancel()
    }

    websocketConnectionExponentialRetry()
  }

  private def onConnectionFailed(status: StatusCode): Unit = {
    println(s"WebSocket connection failed: $status")
    if(webSocketState == CONNECTED){
      serial.send("WebSocketStateChanged=FAILED")
      webSocketState = DISCONNECTED
    }
    
    if(heartbeatTimer != null){
      heartbeatTimer.cancel()
    }
    
    websocketConnectionExponentialRetry()
  }

  private def onConnectedToWebSocket(statusCode: StatusCode): Unit = {
    webSocketState = CONNECTED
    exponentialRetryCount = 0
    serial.send("WebSocketStateChanged=CONNECTED")

    println(s"WebSocket connection established ${dateTimeString()}")
    connectBoxToServer()

    heartbeatTimer = system.scheduler.scheduleWithFixedDelay(
      initialDelay = 20.seconds,
      delay = 20.seconds
    )(() => sendHeartbeat())
  }

  private def sendHeartbeat(): Unit = {
    webSocketClient.send("heartbeat")
  }

  private def connectToWebsocket(): Unit = {
    val websocketUrl = if (uniqueId == "") baseUrl else baseUrl + "?uniqueId=" + uniqueId
    webSocketClient = WebSocketClient(websocketUrl)

    webSocketClient.onReceiveMessage = onReceiveWebSocketMessage
    webSocketClient.onConnectionEstablished = onConnectedToWebSocket
    webSocketClient.onConnectionFailed = onConnectionFailed
    webSocketClient.onConnectionClosed = onWebSocketConnectionClosed
  }

  private def websocketConnectionExponentialRetry(): Unit = {
    if (exponentialRetryCount >= exponentialRetryMaxCount) {
      println(s"Cancel websocket exponential retry after $exponentialRetryCount try")
      exponentialRetryCount = 0
      return
    }

    after(math.pow(2, exponentialRetryCount).seconds, using = system.scheduler) {
      Future {
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
            if (commandResponse.uniqueId != uniqueId) {
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
            onAnecdoteTellerPicked()
          }
        case CommandType.GAME_MODE_CHANGED =>
          if (isCommandSuccessful) {
            commandResponse.message match {
              case Some(message) => onGameModeChanged(message)
              case None =>
            }
          }
        case CommandType.STICK_SCANNED =>
          if (isCommandSuccessful) {
            onStickScanned()
          }
        case _ => println(s"Unmanaged response command type (${commandResponse.commandType})")
      }
    } catch {
      case e: DeserializationException => println(e)
      case e => e.printStackTrace()
    }
  }

  private def onArduinoInitFinished(state: String): Unit = {
    println("ArduinoBox init completed")
    println("Try to connect to websocket ...")
    connectToWebsocket()
  }

  private def onGameStateChanged(state: String): Unit = {
    serial.send(MessageKey.GameStateChanged + '=' + state)
  }

  private def onStickExploded(): Unit = {
    //    serial.send(MessageKey.StickExploded + "=true")
  }

  private def onStickScanned(): Unit = {
    serial.send(MessageKey.StickScanned + "=true")
  }

  private def onRequestChangeGameState(value: String): Unit = {
    val state = GameState.valueOf(value)

    var command = ""

    state match {
      case START => command = CommandType.START_GAME
      case STOP => command = CommandType.STOP_GAME
      case IDLE => command = CommandType.IDLE_GAME
      case _ =>
    }

    if (command != "") {
      webSocketClient.send(JsObject(
        "boxId" -> JsNumber(boxId),
        "uniqueId" -> JsString(uniqueId),
        "commandType" -> JsString(command)
      ))
    }
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

    ProcessBuilder("sudo", "poweroff").start
    System.exit(0)
  }

  private def onRequestWebsocketConnecting(value: String): Unit = {
    println("Try to connect to websocket after button push ...")
    connectToWebsocket()
  }

  private def onAnecdoteTellerPicked(): Unit = {
    // MOCK
    //    webSocketClient.send(JsObject(
    //      "boxId" -> JsNumber(boxId),
    //      "uniqueId" -> JsString(uniqueId),
    //      "commandType" -> JsString(CommandType.VOICE_FLOW),
    //      "payload" -> JsNull
    //    ))
    //
    //    return

    // todo: make anecdote max duration configurable
    val duration = 2.minutes
    val sink = Sink.foreach[ByteString](data => {
      val base64Encoded = Base64.getEncoder.encodeToString(data.toArray)
      webSocketClient.send(JsObject(
        "boxId" -> JsNumber(boxId),
        "uniqueId" -> JsString(uniqueId),
        "commandType" -> JsString(CommandType.VOICE_FLOW),
        "payload" -> JsString(base64Encoded)
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
  if (!os.exists(path)) {
    println("File not exists")
    return ""
  }

  try {
    println(s"Read uniqueId file : $path")
    val lines = os.read.lines(path)
    val uniqueId = os.read.lines(path).apply(0)
    val timestamp = os.read.lines(path).apply(1)
    val dateTime = DateTime.fromIsoDateTimeString(timestamp)

    dateTime match {
      case None =>
        os.remove(path)
        ""
      case Some(dt) =>
        if ((DateTime.now.clicks - dt.clicks) / 60000 > uniqueIdValidDuration)
          os.remove(path)
          ""
        else
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
