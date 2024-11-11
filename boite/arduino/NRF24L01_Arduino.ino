#include <SPI.h>
#include <RF24.h>

RF24 radio(9, 10); // CE, CSN pins

const byte address[6] = "00001"; // Adresse pour la communication NRF24L01

void setup()
{
    Serial.begin(9600);
    radio.begin();
    radio.openReadingPipe(0, address);
    radio.setPALevel(RF24_PA_LOW);
    radio.startListening();
}

void loop()
{
    if (radio.available())
    {
        char receivedText[32] = "";
        radio.read(&receivedText, sizeof(receivedText));
        Serial.println(receivedText);

        // Lecture des commandes série
        if (Serial.available())
        {
            String command = Serial.readStringUntil('\n');
            if (command == "ACTIVATE_LED")
            {
                Serial.println("LED activée");
                // Ajouter ici le code pour activer la LED
            }
            else if (command == "DEACTIVATE_LED")
            {
                Serial.println("LED désactivée");
                // Ajouter ici le code pour désactiver la LED
            }
            else if (command == "READ_SENSOR")
            {
                // Simuler des données de capteur
                Serial.println("Température : 23.5°C, Humidité : 60%");
            }
        }
    }
}
