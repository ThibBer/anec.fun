#include <SPI.h>
#include "SR04.h"
#include "RF24.h"

#define BUTTON_SHORT_PRESS_DURATION 100
#define BUTTON_LONG_PRESS_DURATION 3000

#define GAME_STATE_LED_RED A0
#define GAME_STATE_LED_GREEN A1
#define GAME_STATE_LED_BLUE A2

#define EMOTION_MODE_LED 9
#define THEME_MODE_LED 10
#define SPEAKER_PIN 4
#define MODE_SELECTION_PIN 2

#define PIN_BUTTON_START_GAME 3

#define ULTRASONIC_SENSOR_TRIG_PIN A3
#define ULTRASONIC_SENSOR_ECHO_PIN A4

#define CE_PIN 7
#define CSN_PIN 8

enum GameMode { THEME,
                EMOTION,
                REQUESTED };
enum GameState { START,
                 STOP,
                 STARTED,
                 ROUND_STARTED,
                 STICK_EXPLODED,
                 VOTING,
                 ROUND_STOPPED,
                 STOPPED,
                 PAUSED,
                 ERROR,
                 SHUTDOWN };

const String gameStateLabels[] = { "START", "STOP", "STARTED", "ROUND_STARTED", "STICK_EXPLODED", "VOTING", "ROUND_STOPPED", "STOPPED", "PAUSED", "ERROR" };
const String gameModeLabels[] = { "THEME", "EMOTION" };

GameMode currentGameMode = THEME;
GameState currentGameState = STOPPED;

unsigned long currentTime = 0;
unsigned long previousTimeButton = 0;
unsigned long previousUltrasonicTime = 0;
unsigned long previousTimeModeSelection = 0;
unsigned long startButtonPressedTime = -1;

SR04 sr04 = SR04(ULTRASONIC_SENSOR_ECHO_PIN, ULTRASONIC_SENSOR_TRIG_PIN);

RF24 radio(CE_PIN, CSN_PIN);

const byte endOfSequence[] = { -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127 };
bool isEndOfSequenceReceived = false;
unsigned long latestAudioDataReceived = -1;
const unsigned long MAX_DELAY_AUDIO = 2000;

const uint64_t addresses[2] = { 0xABCDABCD71LL, 0x544d52687CLL };
enum Status {
  IDLE,
  RECEIVED
};

Status status = IDLE;
unsigned long maxTimeAudio = 0;
byte audioData[32];  // Set up a buffer for the received data

byte samplesToDisplay = 32;  // Change this to 32 to send the entire payload over USB/Serial


String gameModeToString(GameMode gameMode);
String gameStateToString(GameState gameState);
GameMode stringToGameMode(String input);
GameState stringToGameState(String input);
void onGameModeChanged(GameMode gameMode);
void onButtonGameModeClick(GameMode gameMode);
void setGameState(GameState newGameState);
void onGameStateChanged(GameState state);
void onReceiveSerialData(String key, String value);
void blinkStartLED(int count, int timing);
void blinkStartLED(int count, int r, int g, int b, int timing);
void blinkStartLED(int count);
void blinkStartLED(int count, int r, int g, int b);
void setGameStateLedColor(int state);
void setGameStateLedColor(int r, int g, int b);
void playSound(int toneHz, int msDuration);
void playButtonClickSound();
void startCheckupSequence();
void askAudio(int time);
void playShutdownSound();
void onWebSocketStateChanged(String value);

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(MODE_SELECTION_PIN, INPUT);
  pinMode(PIN_BUTTON_START_GAME, INPUT);

  pinMode(EMOTION_MODE_LED, OUTPUT);
  pinMode(THEME_MODE_LED, OUTPUT);

  pinMode(GAME_STATE_LED_RED, OUTPUT);
  pinMode(GAME_STATE_LED_GREEN, OUTPUT);
  pinMode(GAME_STATE_LED_BLUE, OUTPUT);

  pinMode(SPEAKER_PIN, OUTPUT);

  // initialize the transceiver on the SPI bus
  if (!radio.begin()) {
    Serial.println(F("radio hardware is not responding!!"));
    while (1) {
      blinkStartLED(5, 255, 0, 0, 100);
      delay(300);
    }  // hold in infinite loop
  }
  radio.setChannel(1);             // Set RF channel to 1
  radio.setAutoAck(0);             // Disable ACKnowledgement packets to allow multicast reception
  radio.setCRCLength(RF24_CRC_8);  // Only use 8bit CRC for audio
  radio.setPALevel(RF24_PA_LOW);
  radio.startListening();

  // radio.setDataRate(RF24_1MBPS);  // Library default is RF24_1MBPS for RF24 and RF24Audio
  radio.openWritingPipe(addresses[0]);     // Set up reading and writing pipes.
  radio.openReadingPipe(1, addresses[1]);  // All of the radios listen by default to the same multicast pipe

  //startCheckupSequence();

  int modeSelectionSwitch = digitalRead(MODE_SELECTION_PIN);
  bool isGameModeEmotion = modeSelectionSwitch == EMOTION;
  digitalWrite(EMOTION_MODE_LED, isGameModeEmotion ? HIGH : LOW);
  digitalWrite(THEME_MODE_LED, modeSelectionSwitch == THEME ? HIGH : LOW);

  if (isGameModeEmotion) {
    onButtonGameModeClick(EMOTION);  // No need to check GameMode.THEME because it's default mode
  }
}

void serialEvent() {
  if (Serial.available() > 0) {
    String serialData = Serial.readString();
    serialData.trim();

    int equalIndex = serialData.indexOf('=');
    if (equalIndex != -1) {
      String key = serialData.substring(0, equalIndex);
      String value = serialData.substring(equalIndex + 1);

      onReceiveSerialData(key, value);
    }
  }
}

void loop() {
  currentTime = millis();

  if (status == RECEIVED) {
    if (latestAudioDataReceived != -1 && currentTime - latestAudioDataReceived > MAX_DELAY_AUDIO) {
      isEndOfSequenceReceived = true;
      for (int i = 0; i < samplesToDisplay; i++) {
        Serial.write(endOfSequence[i]);
      }
    }
    if (isEndOfSequenceReceived) {
      status = IDLE;
      latestAudioDataReceived = -1;
      isEndOfSequenceReceived = false;
      Serial.write('\n');
      return;
    }

    if (radio.available()) {
      latestAudioDataReceived = currentTime;
      radio.read(&audioData, 32);
      for (int i = 0; i < samplesToDisplay; i++) {
        Serial.write(audioData[i]);
      }
    }
    return;
  }

  if (currentTime - previousTimeModeSelection >= 100 && currentGameState == STOPPED) {
    int modeSelectionSwitch = digitalRead(MODE_SELECTION_PIN);

    if (modeSelectionSwitch == EMOTION && currentGameMode == THEME) {
      playButtonClickSound();
      onButtonGameModeClick(EMOTION);
    } else if (modeSelectionSwitch == THEME && currentGameMode == EMOTION) {
      playButtonClickSound();
      onButtonGameModeClick(THEME);
    }

    previousTimeModeSelection = currentTime;
  }

  int requestGameStartButton = digitalRead(PIN_BUTTON_START_GAME);
  if (requestGameStartButton == HIGH) {
    if (startButtonPressedTime == -1) {
      startButtonPressedTime = currentTime;
    }

    // Long press
    if (currentTime - startButtonPressedTime >= BUTTON_LONG_PRESS_DURATION && currentGameState != SHUTDOWN) {
      Serial.println("RequestShutdown=true");
      currentGameState = SHUTDOWN;
      playShutdownSound();
      startButtonPressedTime = -1;
    }
  } else {
    if (startButtonPressedTime != -1) {
      // Short press
      if (currentTime - startButtonPressedTime >= BUTTON_SHORT_PRESS_DURATION && currentTime - startButtonPressedTime < BUTTON_LONG_PRESS_DURATION) {
        Serial.println("debug: short press");
        if (currentGameState == STOPPED) {
          playButtonClickSound();
          setGameState(START);
        } else if (currentGameState == STARTED || currentGameState == PAUSED || currentGameState == ROUND_STOPPED) {
          playButtonClickSound();
          setGameState(STOP);
        }
      }

      startButtonPressedTime = -1;
    }
  }

  if (currentTime - previousUltrasonicTime >= 200 && (currentGameState == ROUND_STOPPED || currentGameState == STARTED)) {
    if (sr04.Distance() <= 10) {
      playButtonClickSound();
      Serial.println("HandDetected=true");
    }

    previousUltrasonicTime = currentTime;
  }
}

void startCheckupSequence() {
  int delayDuration = 250;

  digitalWrite(EMOTION_MODE_LED, HIGH);
  delay(delayDuration);
  digitalWrite(EMOTION_MODE_LED, LOW);
  delay(delayDuration);

  digitalWrite(THEME_MODE_LED, HIGH);
  delay(delayDuration);
  digitalWrite(THEME_MODE_LED, LOW);
  delay(delayDuration);

  analogWrite(GAME_STATE_LED_RED, 255);
  delay(delayDuration);
  analogWrite(GAME_STATE_LED_RED, 0);
  delay(delayDuration);

  analogWrite(GAME_STATE_LED_GREEN, 255);
  delay(delayDuration);
  analogWrite(GAME_STATE_LED_GREEN, 0);
  delay(delayDuration);

  analogWrite(GAME_STATE_LED_BLUE, 255);
  delay(delayDuration);
  analogWrite(GAME_STATE_LED_BLUE, 0);
  delay(delayDuration);

  analogWrite(GAME_STATE_LED_RED, 255);
  analogWrite(GAME_STATE_LED_GREEN, 255);
  analogWrite(GAME_STATE_LED_BLUE, 255);
  delay(delayDuration);
  analogWrite(GAME_STATE_LED_RED, 0);
  analogWrite(GAME_STATE_LED_GREEN, 0);
  analogWrite(GAME_STATE_LED_BLUE, 0);
  delay(delayDuration);

  playSound(800, delayDuration);
  delay(delayDuration);
}

void blinkStartLED(int count) {
  blinkStartLED(count, 255, 255, 255, 200);
}

void blinkStartLED(int count, int r, int g, int b) {
  blinkStartLED(count, r, g, b, 200);
}

void blinkStartLED(int count, int timing) {
  blinkStartLED(count, 255, 255, 255, timing);
}

void blinkStartLED(int count, int r, int g, int b, int timing) {
  for (int i = 0; i < count; i++) {
    setGameStateLedColor(r, g, b);
    delay(timing);
    setGameStateLedColor(0);
    delay(timing);
  }
}

void onReceiveSerialData(String key, String value) {
  if (key == "GameStateChanged") {
    onGameStateChanged(stringToGameState(value));
  } else if (key == "GameModeChanged") {
    onGameModeChanged(stringToGameMode(value));
  } else if (key == "VoiceFlowStart") {
    askAudio(value.toInt());
  } else if (key == "WebSocketStateChanged") {
    onWebSocketStateChanged(value);
  }
}

void onGameModeChanged(GameMode gameMode) {
  switch (gameMode) {
    case EMOTION:
      digitalWrite(THEME_MODE_LED, LOW);
      digitalWrite(EMOTION_MODE_LED, HIGH);
      break;
    case THEME:
      digitalWrite(THEME_MODE_LED, HIGH);
      digitalWrite(EMOTION_MODE_LED, LOW);
      break;
  }

  currentGameMode = gameMode;
}

void onWebSocketStateChanged(String value){
  if(value == "CONNECTED"){
    blinkStartLED(2, 0, 255, 0, 200);
    return;
  }

  if(value == "FAILED" || value == "CLOSED"){
    blinkStartLED(2, 255, 0, 0, 200);
  }
}

void setGameStateLedColor(int state) {
  setGameStateLedColor(state, state, state);
}

void setGameStateLedColor(int r, int g, int b) {
  analogWrite(GAME_STATE_LED_RED, r);
  analogWrite(GAME_STATE_LED_GREEN, g);
  analogWrite(GAME_STATE_LED_BLUE, b);
}

void onButtonGameModeClick(GameMode gameMode) {
  Serial.print("GameMode=");
  Serial.println(gameModeToString(gameMode));

  currentGameMode = REQUESTED;
}

String gameModeToString(GameMode gameMode) {
  return gameModeLabels[gameMode];
}

String gameStateToString(GameState gameMode) {
  return gameStateLabels[gameMode];
}

GameMode stringToGameMode(String input) {
  if (input == "EMOTION") {
    return EMOTION;
  }

  if (input == "THEME") {
    return THEME;
  }
}

GameState stringToGameState(String input) {
  if (input == "START") {
    return START;
  }

  if (input == "STOP") {
    return STOP;
  }

  if (input == "STARTED") {
    return STARTED;
  }

  if (input == "ROUND_STARTED") {
    return ROUND_STARTED;
  }

  if (input == "STICK_EXPLODED") {
    return STICK_EXPLODED;
  }

  if (input == "VOTING") {
    return VOTING;
  }

  if (input == "ROUND_STOPPED") {
    return ROUND_STOPPED;
  }

  if (input == "PAUSED") {
    return PAUSED;
  }

  if (input == "ERROR") {
    return ERROR;
  }

  if (input == "STOPPED") {
    return STOPPED;
  }
}

// Set game state and write "SetGameState={gameState}" to serial
void setGameState(GameState gameState) {
  Serial.print("SetGameState=");
  Serial.println(gameStateToString(gameState));

  currentGameState = gameState;
}

// Handle new game state sent by the server
void onGameStateChanged(GameState newGameState) {
  if (newGameState == currentGameState) {
    return;
  }

  switch (newGameState) {
    case STARTED:
      playSound(1000, 150);
      delay(400);
      playSound(1000, 150);
      delay(400);
      playSound(1000, 150);
      delay(400);
      playSound(800, 600);

      setGameStateLedColor(255);

      break;
    case ERROR:
      if (currentGameState == START || currentGameState == STOP) {
        currentGameState = STOPPED;
      }

      break;
    case STOPPED:
      setGameStateLedColor(0);

      playSound(800, 300);
      delay(500);
      playSound(800, 300);
      delay(500);
      playSound(800, 300);

      break;
  }

  if (newGameState != ERROR) {
    currentGameState = newGameState;
  }
}

void playButtonClickSound() {
  playSound(600, 200);
}

void playSound(int toneHz, int msDuration) {
  tone(SPEAKER_PIN, toneHz, msDuration);
}

void playShutdownSound() {
  playSound(800, 500);
  delay(200);
  playSound(400, 1000);
}

void askAudio(int time) {
  byte com[2] = { 1, time };
  radio.stopListening();
  radio.write(&com, sizeof(com));
  radio.startListening();
  status = RECEIVED;
  maxTimeAudio = millis() + (unsigned long)(time * 1000);
}
