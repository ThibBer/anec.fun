import com.pi4j.io.spi.SpiFactory // Bibliothèque Pi4J pour l'interface SPI
import com.pi4j.io.spi.SpiDevice // Interface pour la communication SPI
import java.nio.charset.StandardCharsets // Pour la gestion des chaînes de caractères

object NRF24Communication {
  // Initialisation du périphérique SPI
  val spiDevice: SpiDevice = SpiFactory.getInstance(
    SpiDevice.DEFAULT_SPI_BUS, // Bus SPI par défaut (0)
    SpiDevice.DEFAULT_SPI_DEVICE, // Périphérique SPI par défaut (CS0)
    500000 // Vitesse de communication SPI (500 kHz)
  )

  def main(args: Array[String]): Unit = {
    println("Initialisation du module NRF24L01...")

    // Envoi de commandes à l'Arduino pour tester la communication
    sendCommand("LED_ON") // Commande pour allumer la LED
    Thread.sleep(2000) // Attente de 2 secondes
    sendCommand("LED_OFF") // Commande pour éteindre la LED

    // Boucle de lecture des données du capteur de pression
    while (true) {
      val response = readData() // Lecture des données envoyées par l'Arduino
      if (response.startsWith("PRESSURE")) {
        // Extraction et affichage de la valeur de pression
        val pressureValue = response.split(":")(1).toInt
        println(s"Valeur de pression : $pressureValue")
      }
      Thread.sleep(500) // Pause de 500 ms pour éviter une surcharge de lecture
    }
  }

  // Fonction pour envoyer une commande à l'Arduino via le module NRF24L01
  def sendCommand(command: String): Unit = {
    spiDevice.write(
      command.getBytes(StandardCharsets.UTF_8)
    ) // Envoi de la commande
    println(s"Commande envoyée : $command")
  }

  // Fonction pour lire les données reçues du module NRF24L01
  def readData(): String = {
    val buffer = new Arrayn pour les données reçues(32 octets max)
    spiDevice.read(buffer, 0, buffer.length) // Lecture des données
    new String(
      buffer,
      StandardCharsets.UTF_8
    ).trim // Conversion des octets en chaîne de caractères
  }
}
