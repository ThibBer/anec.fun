package services

import com.fazecast.jSerialComm.SerialPort
import utils.Logger

object NRF24L01Service {
  private var serialPort: SerialPort = _

  // Initialiser le port série
  def initSerialCommunication(): Unit = {
    serialPort =
      SerialPort.getCommPorts.find(_.getSystemPortName.contains("ttyUSB"))
    if (serialPort != null) {
      serialPort.setBaudRate(9600)
      serialPort.setNumDataBits(8)
      serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT)
      serialPort.setParity(SerialPort.NO_PARITY)
      serialPort.openPort()

      if (serialPort.isOpen) {
        Logger.log("Communication série initialisée avec succès.")
      } else {
        Logger.log("Erreur : Impossible d'ouvrir le port série.")
      }
    } else {
      Logger.log("Erreur : Aucun port série détecté.")
    }
  }

  // Envoyer une commande à l'Arduino
  def sendCommand(command: String): Unit = {
    if (serialPort != null && serialPort.isOpen) {
      try {
        val data = s"$command\n".getBytes
        serialPort.writeBytes(data, data.length)
        Logger.log(s"Commande envoyée : $command")
      } catch {
        case e: Exception =>
          Logger.log(s"Erreur lors de l'envoi de la commande : ${e.getMessage}")
      }
    } else {
      Logger.log("Erreur : Le port série n'est pas ouvert.")
    }
  }

  // Lire les données du capteur
  def readSensor(): String = {
    if (serialPort != null && serialPort.isOpen) {
      try {
        val buffer = new Arrayl numRead =
          serialPort.readBytes(buffer, buffer.length)
        if (numRead > 0) {
          val response = new String(buffer, 0, numRead).trim
          Logger.log(s"Données reçues : $response")
          return response
        }
      } catch {
        case e: Exception =>
          Logger.log(s"Erreur lors de la lecture des données : ${e.getMessage}")
      }
    }
    "Erreur de lecture"
  }

  // Fermer le port série
  def closeSerialCommunication(): Unit = {
    if (serialPort != null && serialPort.isOpen) {
      serialPort.closePort()
      Logger.log("Port série fermé.")
    }
  }
}
