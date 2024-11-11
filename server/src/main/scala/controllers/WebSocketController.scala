package controllers

import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import services.NRF24L01Service
import utils.Logger

object WebSocketController {

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].collect {
    case TextMessage.Strict(text) =>
      Logger.log(s"Message reçu : $text")
      val response = handleMessage(text)
      TextMessage(response)
  }

  def handleMessage(message: String): String = {
    message match {
      case "ACTIVATE_LED" =>
        NRF24L01Service.sendCommand("ACTIVATE_LED")
        "Commande envoyée : LED activée"
      case "DEACTIVATE_LED" =>
        NRF24L01Service.sendCommand("DEACTIVATE_LED")
        "Commande envoyée : LED désactivée"
      case "READ_SENSOR" =>
        NRF24L01Service.readSensor()
      case _ =>
        Logger.log(s"Commande inconnue : $message")
        "Commande non reconnue"
    }
  }
}
