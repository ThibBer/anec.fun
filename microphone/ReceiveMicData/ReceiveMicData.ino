/*
 * See documentation at https://nRF24.github.io/RF24
 * See License information at root directory of this library
 * Author: Brendan Doherty 2bndy5
 */

/**
 * A simple example of streaming data from 1 nRF24L01 transceiver to another.
 *
 * This example was written to be used on 2 devices acting as "nodes".
 * Use the Serial Monitor to change each node's behavior.
 */
#include <SPI.h>
#include "printf.h"
#include "RF24.h"

#define CE_PIN 7
#define CSN_PIN 8

RF24 radio(CE_PIN, CSN_PIN);

const uint64_t addresses[2] = { 0xABCDABCD71LL, 0x544d52687CLL };

void setup() {
  Serial.begin(115200);
  while (!Serial) {
    // some boards need to wait to ensure access to serial over USB
  }

  // initialize the transceiver on the SPI bus
  if (!radio.begin()) {
    Serial.println(F("radio hardware is not responding!!"));
    while (1) {}  // hold in infinite loop
  }
  radio.setChannel(1);             // Set RF channel to 1
  radio.setAutoAck(0);             // Disable ACKnowledgement packets to allow multicast reception
  radio.setCRCLength(RF24_CRC_8);  // Only use 8bit CRC for audio
  //radio.setDataRate(RF24_1MBPS);  // Library default is RF24_1MBPS for RF24 and RF24Audio

  // print example's introductory prompt
  Serial.println(F("Program: ReceiveMicData"));

  // Set the PA Level low to try preventing power supply related problems
  // because these examples are likely run with nodes in close proximity to
  // each other.
  radio.setPALevel(RF24_PA_LOW);  // RF24_PA_MAX is default.

  // save on transmission time by setting the radio to only transmit the
  // number of bytes we need to transmit
  // set the RX address of the TX node into a RX pipe
  radio.openWritingPipe(addresses[0]);     // Set up reading and writing pipes.
  radio.openReadingPipe(1, addresses[1]);  // All of the radios listen by default to the same multicast pipe
  radio.startListening();                  // put radio in RX mode


  // For debugging info
  printf_begin();  // needed only once for printing details
  // radio.printDetails();       // (smaller) function that prints raw register values
  radio.printPrettyDetails();  // (larger) function that prints human readable data

}  // setup()


byte audioData[32];  // Set up a buffer for the received data

byte samplesToDisplay = 32;  // Change this to 32 to send the entire payload over USB/Serial

void loop() {
  if (radio.available()) {
    radio.read(&audioData, 32);
    // Now do whatever you want with the audio data.
    // Maybe send it over USB to a PC to analyze further?

    for (int i = 0; i < samplesToDisplay; i++) {
      Serial.write(audioData[i]);
    }
    // Serial.println("");

    // Note: The Audio library default is 24kHZ sample rate which results in 750 payloads/second with 8bit audio
    // Decrease the audio sample rate via userConfig.h if the data stream is too fast, or increase the Serial baud rate
  }
}
