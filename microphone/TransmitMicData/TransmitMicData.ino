/* RF24 Audio Library TMRh20 2014

This sketch is intended to demonstrate the basic functionality of the audio library.

Requirements:
2 Arduinos (Uno,Nano,Mega, etc supported)
2 NRF24LO1 Radio Modules
1 or more input devices (microphone, ipod, etc)
1 or more output devices (speaker, amplifier, etc)

Setup:
1. Change the CE,CS pins below to match your chosen pins (I use 7,8 on 328 boards, and 48,49 on Mega boards)
2. Upload this sketch to two or more devices
3. Send serial commands via the serial monitor to control transmission and volume (volume only affects receiving devices)

Default Pin Selections:
Speaker: pins 9,10 on UNO, Nano,  pins 11,12 on Mega 2560
Input/Microphone: Analog pin A0 on all boards

*/

#include <RF24.h>
#include <SPI.h>
#include "RF24Audio.h"
#include "printf.h"  // General includes for radio and audio lib

#define PIN_BUTTON 7
#define PIN_LED 5

const uint64_t addresses[2] = { 0xABCDABCD71LL, 0x544d52687CLL };

RF24 radio(2, 3);             // Set radio up using pins 2 (CE) 3 (CS)
RF24Audio rfAudio(radio, 0);  // Set up the audio using the radio, and set to radio number 0

void makePayload();
void sendEoS();
void blinkLED(int count, int delay);
void blinkLED(int count);

void setup() {
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  pinMode(PIN_LED, OUTPUT);

  Serial.begin(115200);
  // initialize the transceiver on the SPI bus
  if (!radio.begin()) {
    Serial.println(F("radio hardware is not responding!!"));
    while (1) {
      blinkLED(3, 100);
      blinkLED(2, 200);
      delay(500);
    }  // hold in infinite loop
  }
  radio.setChannel(1);             // Set RF channel to 1
  radio.setAutoAck(0);             // Disable ACKnowledgement packets to allow multicast reception
  radio.setCRCLength(RF24_CRC_8);  // Only use 8bit CRC for audio
  // radio.setDataRate(RF24_1MBPS);  // Library default is RF24_1MBPS for RF24 and RF24Audio

  // print example's introductory prompt
  Serial.println(F("Program: TransmitMicData"));

  // Set the PA Level low to try preventing power supply related problems
  // because these examples are likely run with nodes in close proximity to
  // each other.
  // radio.setPALevel(RF24_PA_LOW);  // RF24_PA_MAX is default.

  // // save on transmission time by setting the radio to only transmit the
  // // number of bytes we need to transmit
  // // set the RX address of the TX node into a RX pipe
  // rfAudio.begin();              // Start up the radio and audio libararies
  radio.openWritingPipe(addresses[1]);     // Set up reading and writing pipes.
  radio.openReadingPipe(1, addresses[0]);  // All of the radios listen by default to the same multicast pipe
  // radio.startListening();                  // put radio in RX mode
  // radio.stopListening();                  // put radio in TX mode
  // rfAudio.broadcast(255);
  // rfAudio.transmit();

  toggleMode(false);
  printf_begin();  // needed only once for printing details
  radio.printPrettyDetails();
}

uint32_t printTimer = 0;
bool isTransmit = false;
unsigned long maxTime = 0;
unsigned long currentTime = 0;

byte audioData[32];  // Set up a buffer for the received data
byte command[32];

void toggleMode(bool mode) {
  isTransmit = mode;
  if (mode) {
    Serial.println("Start transmitting");
    radio.stopListening();
    digitalWrite(PIN_LED, HIGH);
  } else {
    digitalWrite(PIN_LED, LOW);
    delay(200);
    Serial.println("Stop transmitting");
    radio.startListening();
    
  }
}


void loop() {
  currentTime = millis();
  if (isTransmit) {
    if (currentTime > maxTime || digitalRead(PIN_BUTTON) == LOW) {
      toggleMode(false);
      return;
    }

    makePayload();
    if (!radio.writeFast(&audioData, 32)) {
      Serial.println("Error");
    }
  } else {
    if (radio.available()) {
      radio.read(&command, 32);
      if (command[0] == 1) {
        maxTime = currentTime + ((unsigned long)command[1] * 1000);
        toggleMode(true);
      }
    }
  }
}

void makePayload() {
  for (int i = 0; i < 32; i++) {
    audioData[i] = map(analogRead(A0), 0, 1023, 0, 255);
    // Serial.write(audioData[i]);
  }
}


void blinkLED(int count) {
  blinkLED(count, 200);
}
void blinkLED(int count, int timing) {
  for (int i = 0; i < count; i++) {
    delay(timing);
    digitalWrite(PIN_LED, HIGH);
    delay(timing);
    digitalWrite(PIN_LED, LOW);
  }
}
