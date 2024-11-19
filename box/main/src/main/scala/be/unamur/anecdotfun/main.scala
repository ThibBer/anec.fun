package be.unamur.anecdotfun

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{DateTime, StatusCode}
import be.unamur.anecdotfun.GameState.{START, STOP}
import com.typesafe.config.ConfigFactory
import spray.json._
import be.unamur.anecdotfun.CommandResponseJsonProtocol.commandResponseFormat

val config = ConfigFactory.load()
implicit val system: ActorSystem = ActorSystem("box-anecdotfun", config)

val boxId = config.getInt("akka.game.box.id")
var webSocketClient = WebSocketClient("ws://localhost:8080/ws/" + boxId)
val serial = SerialThread(config.getString("akka.game.arduino.com-port"))
var uniqueId = ""

val serialMessageAction: Map[String, String => Unit] = Map(
  MessageKey.SetGameState -> onRequestChangeGameState,
  MessageKey.Mode -> onRequestChangeMode
)

@main
def main(): Unit = {
  serial.onReceiveSerialData = onReceiveSerialData

  webSocketClient.onReceiveMessage = onReceiveWebSocketMessage
  webSocketClient.onConnectionEstablished = onConnectedToWebSocket
  webSocketClient.onConnectionFailed = (status: StatusCode) => println(s"WebSocket connection failed: $status")
  webSocketClient.onConnectionClosed = () => println(s"WebSocket connection closed ${dateTimeString()}")
}

def onConnectedToWebSocket(statusCode: StatusCode): Unit = {
  println(s"WebSocket connection established ${dateTimeString()}")

  serial.start()
  connectBoxToServer()
}

def onReceiveSerialData(serialInput: String): Unit = {
  println("Received from serial : " + serialInput)

  if(serialInput.toLowerCase().contains("debug")){ // ignore debug message sent from serial
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

def connectBoxToServer(): Unit = {
  webSocketClient.send(JsObject(
    "box_id" -> JsNumber(boxId),
    "commandType" -> JsString(CommandType.CONNECT_BOX),
    "uniqueId" -> JsString(uniqueId)
  ))
}

def onReceiveWebSocketMessage(message: String): Unit = {
  println("Receive websocket message : " + message)

  try{
    val commandResponse = message.parseJson.convertTo[CommandResponse]

    if(commandResponse.status != CommandStatus.SUCCESS){
      println("Failed command : " + commandResponse.commandType)
      return
    }

    commandResponse.commandType match {
      case CommandType.CONNECTION => uniqueId = commandResponse.uniqueId
      case CommandType.START_GAME => onGameStateChanged("STARTED")
      case CommandType.STOP_GAME => onGameStateChanged("STOPPED")
      case _ => println(s"Unmanaged response command type (${commandResponse.commandType})")
    }
  }catch{
    case e: DeserializationException => println(e)
    case e => e.printStackTrace()
  }
}

def onGameStateChanged(state: String): Unit = {
  serial.send(MessageKey.GameStateChanged + '=' + state)
}

def onRequestChangeGameState(value: String): Unit = {
  val state = GameState.valueOf(value)

  var command = ""

  state match {
    case START => command = CommandType.START_GAME
    case STOP => command = CommandType.STOP_GAME
    case _ =>
  }

  val obj = JsObject(
    "boxId" -> JsNumber(boxId),
    "uniqueId" -> JsString(uniqueId),
    "commandType" -> JsString(command)
  )

  webSocketClient.send(obj)
}

def onRequestChangeMode(value: String): Unit = {
  println("The box does not currently support mode switching")
}

def dateTimeString(): String = {
  val dateTime = DateTime.now
  s"${dateTime.day}/${dateTime.month}/${dateTime.year} ${dateTime.hour}:${dateTime.minute}:${dateTime.second}"
}

def twoDigitsNumber(data: Int) : String = if (data <= 9) "0" + data else data.toString
