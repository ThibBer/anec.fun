#include "SR04.h"

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

enum GameMode {THEME, EMOTION, REQUESTED};
enum GameState {START, STOP, STARTED, ROUND_STARTED, STICK_EXPLODED, VOTING, ROUND_STOPPED, STOPPED, PAUSED, ERROR, SHUTDOWN};

const String gameStateLabels[] = {"START", "STOP", "STARTED", "ROUND_STARTED", "STICK_EXPLODED", "VOTING", "ROUND_STOPPED", "STOPPED", "PAUSED", "ERROR"};
const String gameModeLabels[] = {"THEME", "EMOTION"};

GameMode currentGameMode = THEME;
GameState currentGameState = STOPPED;

unsigned long currentTime = 0;
unsigned long previousTimeButton = 0;
unsigned long previousUltrasonicTime = 0;
unsigned long previousTimeModeSelection = 0;
unsigned long startButtonPressedTime = -1;

SR04 sr04 = SR04(ULTRASONIC_SENSOR_ECHO_PIN, ULTRASONIC_SENSOR_TRIG_PIN);

String gameModeToString(GameMode gameMode);
String gameStateToString(GameState gameState);
GameMode stringToGameMode(String input);
GameState stringToGameState(String input);
void onGameModeChanged(GameMode gameMode);
void onButtonGameModeClick(GameMode gameMode);
void processSerialInput();
void setGameState(GameState newGameState);
void onGameStateChanged(GameState state);
void onReceiveSerialData(String key, String value);
void blinkStartLED(int count);
void setGameStateLedColor(int state);
void setGameStateLedColor(int r, int g, int b);
void playSound(int toneHz, int msDuration);
void playButtonClickSound();
void startCheckupSequence();
void playShutdownSound();

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

  startCheckupSequence();

  delay(2000);

  int modeSelectionSwitch = digitalRead(MODE_SELECTION_PIN);
  bool isGameModeEmotion = modeSelectionSwitch == EMOTION;
  digitalWrite(EMOTION_MODE_LED, isGameModeEmotion ? HIGH : LOW);
  digitalWrite(THEME_MODE_LED, modeSelectionSwitch == THEME ? HIGH : LOW);
  
  if(isGameModeEmotion){
    onButtonGameModeClick(EMOTION); // No need to check GameMode.THEME because it's default mode
  }
}

void loop() {
  currentTime = millis();

  processSerialInput();

  if(currentTime - previousTimeModeSelection >= 100 && currentGameState == STOPPED){
    int modeSelectionSwitch = digitalRead(MODE_SELECTION_PIN);

    if(modeSelectionSwitch == EMOTION && currentGameMode == THEME){
      playButtonClickSound();
      onButtonGameModeClick(EMOTION);
    }else if(modeSelectionSwitch == THEME && currentGameMode == EMOTION) {
      playButtonClickSound();
      onButtonGameModeClick(THEME);
    }

    previousTimeModeSelection = currentTime;
  }

  int requestGameStartButton = digitalRead(PIN_BUTTON_START_GAME);
  if(requestGameStartButton == HIGH){
    if(startButtonPressedTime == -1){
      startButtonPressedTime = currentTime;
    }

    // Long press
    if(currentTime - startButtonPressedTime >= BUTTON_LONG_PRESS_DURATION && currentGameState != SHUTDOWN){
      Serial.println("RequestShutdown=true");
      currentGameState = SHUTDOWN;
      playShutdownSound();
    }
  }else{
    if(startButtonPressedTime != -1){
      // Short press
      if(currentTime - startButtonPressedTime >= BUTTON_SHORT_PRESS_DURATION && currentTime - startButtonPressedTime < BUTTON_LONG_PRESS_DURATION){
        if(currentGameState == STOPPED){
          playButtonClickSound();
          setGameState(START);
        }else if(currentGameState == STARTED || currentGameState == PAUSED || currentGameState == ROUND_STOPPED){
          playButtonClickSound();
          setGameState(STOP);
        }
      }

      startButtonPressedTime = -1;
    }
  }

  if(currentTime - previousUltrasonicTime >= 200 && currentGameState == ROUND_STOPPED){
    if(sr04.Distance() <= 10){
      playButtonClickSound();
      Serial.println("HandDetected=true");
    }

    previousUltrasonicTime = currentTime;
  }
}

void startCheckupSequence(){
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

void blinkStartLED(int count){
  for (int i = 0; i < count; i++) {
    setGameStateLedColor(255);
    delay(200);
    setGameStateLedColor(0);
    delay(200);
  }
}

void processSerialInput(){
  if (Serial.available() > 0) {
    String serialData = Serial.readString();
    serialData.trim();

    int equalIndex = serialData.indexOf('=');
    if(equalIndex != -1){
      String key = serialData.substring(0, equalIndex);
      String value = serialData.substring(equalIndex + 1);

      onReceiveSerialData(key, value);
    }
  }
}

void onReceiveSerialData(String key, String value){
  if(key == "GameStateChanged"){
    onGameStateChanged(stringToGameState(value));
  } else if(key == "GameModeChanged"){
    onGameModeChanged(stringToGameMode(value));
  }
}

void onGameModeChanged(GameMode gameMode){
  switch(gameMode){
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

void setGameStateLedColor(int state){
  setGameStateLedColor(state, state, state);
}

void setGameStateLedColor(int r, int g, int b){
  analogWrite(GAME_STATE_LED_RED, r);
  analogWrite(GAME_STATE_LED_GREEN, g);
  analogWrite(GAME_STATE_LED_BLUE, b);
}

void onButtonGameModeClick(GameMode gameMode){
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

GameMode stringToGameMode(String input){
  if(input == "EMOTION"){
    return EMOTION;
  }

  if(input == "THEME"){
    return THEME;
  }
}

GameState stringToGameState(String input){
  if(input == "START"){
    return START;
  }

  if(input == "STOP"){
    return STOP;
  }

  if(input == "STARTED"){
    return STARTED;
  }

  if(input == "ROUND_STARTED"){
    return ROUND_STARTED;
  }

  if(input == "STICK_EXPLODED"){
    return STICK_EXPLODED;
  }

  if(input == "VOTING"){
    return VOTING;
  }

  if(input == "ROUND_STOPPED"){
    return ROUND_STOPPED;
  }

  if(input == "PAUSED"){
    return PAUSED;
  }

  if(input == "ERROR"){
    return ERROR;
  }

  if(input == "STOPPED"){
    return STOPPED;
  }
}

// Set game state and write "SetGameState={gameState}" to serial
void setGameState(GameState gameState){
  Serial.print("SetGameState=");
  Serial.println(gameStateToString(gameState));
  
  currentGameState = gameState;
}

// Handle new game state sent by the server
void onGameStateChanged(GameState newGameState){
  if(newGameState == currentGameState){
    return;
  }

  switch(newGameState){
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
      if(currentGameState == START || currentGameState == STOP){
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

  if(newGameState != ERROR){
    currentGameState = newGameState;
  }
}

void playButtonClickSound(){
  playSound(600, 200);
}

void playSound(int toneHz, int msDuration){
  tone(SPEAKER_PIN, toneHz, msDuration);
}

void playShutdownSound(){
  playSound(800, 500);
  delay(200);
  playSound(400, 1000);
}