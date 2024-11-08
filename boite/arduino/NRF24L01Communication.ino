#include <SPI.h>      // Bibliothèque pour la communication SPI
#include <nRF24L01.h> // Bibliothèque pour le module nRF24L01
#include <RF24.h>     // Bibliothèque RF24 pour la gestion des communications radio

// Configuration du module NRF24L01 : pins CE et CSN
RF24 radio(9, 10);

// Adresse de communication (doit être la même sur l'Arduino et le Raspberry Pi)
const byte address[6] = "00001";

// Déclaration des pins pour les capteurs et actionneurs
int ledPin = 7;       // Pin pour la LED
int pressurePin = A0; // Pin pour le capteur de pression

void setup()
{
    Serial.begin(9600);                // Initialisation de la communication série
    radio.begin();                     // Initialisation du module NRF24L01
    radio.openReadingPipe(0, address); // Ouverture d'un canal de lecture
    radio.setPALevel(RF24_PA_LOW);     // Réglage de la puissance d'émission
    radio.startListening();            // Passage en mode écoute pour recevoir des messages

    pinMode(ledPin, OUTPUT);     // Configuration de la LED en sortie
    pinMode(pressurePin, INPUT); // Configuration du capteur de pression en entrée
}

void loop()
{
    // Vérification si des données ont été reçues
    if (radio.available())
    {
        char receivedData[32] = "";                      // Tampon pour les données reçues
        radio.read(&receivedData, sizeof(receivedData)); // Lecture des données
        Serial.println(receivedData);                    // Affichage des données dans le moniteur série

        // Gestion des commandes reçues pour la LED
        if (strcmp(receivedData, "LED_ON") == 0)
        {
            digitalWrite(ledPin, HIGH); // Allumer la LED
        }
        else if (strcmp(receivedData, "LED_OFF") == 0)
        {
            digitalWrite(ledPin, LOW); // Éteindre la LED
        }
    }

    // Lecture de la valeur du capteur de pression
    int pressureValue = analogRead(pressurePin);
    char message[32];
    // Formatage du message à envoyer (exemple : "PRESSURE:512")
    snprintf(message, sizeof(message), "PRESSURE:%d", pressureValue);

    // Envoi des données au Raspberry Pi
    radio.stopListening();                  // Interruption de l'écoute pour envoyer des données
    radio.write(&message, sizeof(message)); // Envoi des données formatées
    radio.startListening();                 // Reprise de l'écoute

    delay(100); // Petite pause pour éviter une surcharge de la communication
}
