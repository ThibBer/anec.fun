package be.unamur.anecdotfun

import akka.actor.ActorSystem
import be.unamur.anecdotfun.GameState.{START, STOP}
import com.typesafe.config.ConfigFactory
import spray.json.{JsNumber, JsObject, JsString}

val config = ConfigFactory.load()
implicit val system: ActorSystem = ActorSystem("box-anecdotfun", config)

val boxId = 1
val port = "COM5"
var webSocketClient = WebSocketClient("ws://localhost:8080/ws/" + boxId)
val serial = SerialThread(port)

val messageAction: Map[String, String => Unit] = Map(
  MessageKey.SetGameState -> onRequestChangeGameState,
  MessageKey.Mode -> onRequestChangeMode
)

@main
def main(): Unit = {
  serial.onReceiveSerialData = onReceiveSerialData
  serial.start()
  webSocketClient.onReceiveMessage = onReceiveWebSocketMessage
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

      messageAction.get(name) match {
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
    "uniqueId" -> JsString(""),
    "commandType" -> JsString(CommandType.ConnectBox)
  ))
}

def onReceiveWebSocketMessage(message: String): Unit = {
  println("Receive websocket message : " + message)
  if(message.contains("STARTED")){
    onGameStateChanged("STARTED")
  }else if(message.contains("STOPPED")){
    onGameStateChanged("STOPPED")
  }
}

def onGameStateChanged(state: String): Unit = {
  serial.send(MessageKey.GameStateChanged + '=' + state)
}

def onRequestChangeGameState(value: String): Unit = {
  val state = GameState.valueOf(value)

  var command = ""

  state match {
    case START => command = CommandType.StartGame
    case STOP => command = CommandType.StopGame
    case _ =>
  }

  val obj = JsObject(
    "box_id" -> JsNumber(boxId),
    "uniqueId" -> JsString(""),
    "commandType" -> JsString(command)
  )

  webSocketClient.send(obj)
}

def onRequestChangeMode(value: String): Unit = {
  println("The box does not currently support mode switching")
}