#include "SR04.h"

#define GAME_STATE_LED_RED A0
#define GAME_STATE_LED_GREEN A1
#define GAME_STATE_LED_BLUE A2

#define ANECDOTE_MODE_LED 9
#define EMOTION_MODE_LED 10
#define SPEAKER_PIN 4
#define MODE_SELECTION_PIN 2

#define PIN_BUTTON_START_GAME 3

#define ULTRASONIC_SENSOR_TRIG_PIN A3
#define ULTRASONIC_SENSOR_ECHO_PIN A4

enum Theme {EMOTION, ANECDOTE};
enum GameState {START, STOP, STARTED, ROUND_STARTED, STICK_EXPLODED, VOTING, ROUND_STOPPED, STOPPED, PAUSED, ERROR};

const String gameStateLabels[] = {"START", "STOP", "STARTED", "ROUND_STARTED", "STICK_EXPLODED", "VOTING", "ROUND_STOPPED", "STOPPED", "PAUSED", "ERROR"};
const String themeLabels[] = {"EMOTION", "ANECDOTE"};

Theme currentTheme = EMOTION;
GameState gameState = STOPPED;

unsigned long currentTime = 0;
unsigned long previousTimeButton = 0;
unsigned long previousUltrasonicTime = 0;
unsigned long previousTimeModeSelection = 0;

SR04 sr04 = SR04(ULTRASONIC_SENSOR_ECHO_PIN, ULTRASONIC_SENSOR_TRIG_PIN);

String themeToString(Theme theme);
String gameStateToString(GameState gameState);
Theme stringToTheme(String input);
GameState stringToGameState(String input);
void onButtonThemeClick(Theme theme);
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

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(MODE_SELECTION_PIN, INPUT);
  pinMode(PIN_BUTTON_START_GAME, INPUT);

  pinMode(EMOTION_MODE_LED, OUTPUT);
  pinMode(ANECDOTE_MODE_LED, OUTPUT);

  pinMode(GAME_STATE_LED_RED, OUTPUT);
  pinMode(GAME_STATE_LED_GREEN, OUTPUT);
  pinMode(GAME_STATE_LED_BLUE, OUTPUT);

  pinMode(SPEAKER_PIN, OUTPUT);

  startCheckupSequence();

  delay(2000);

  blinkStartLED(3);
}

void loop() {
  currentTime = millis();

  processSerialInput();

  if(currentTime - previousTimeModeSelection >= 100){
    int modeSelectionSwitch = digitalRead(MODE_SELECTION_PIN);

    if(modeSelectionSwitch == EMOTION && currentTheme != EMOTION){
      playButtonClickSound();
      onButtonThemeClick(EMOTION);
    }else if(modeSelectionSwitch == ANECDOTE && currentTheme != ANECDOTE) {
      playButtonClickSound();
      onButtonThemeClick(ANECDOTE);
    }

    previousTimeModeSelection = currentTime;
  }

  if(currentTime - previousTimeButton >= 200){
    int requestGameStartButton = digitalRead(PIN_BUTTON_START_GAME);

    if(requestGameStartButton == HIGH){
      if(gameState == STOPPED){
        playButtonClickSound();
        setGameState(START);
      }else if(gameState == STARTED || gameState == PAUSED || gameState == ROUND_STOPPED){
        playButtonClickSound();
        setGameState(STOP);
      }
    }

    previousTimeButton = currentTime;
  }

  if(currentTime - previousUltrasonicTime >= 200 && gameState == ROUND_STOPPED){
    if(sr04.Distance() <= 10){
      playButtonClickSound();
      Serial.println("HandDetected=true");
    }

    previousUltrasonicTime = currentTime;
  }
}

void startCheckupSequence(){
  int delayDuration = 400;

  digitalWrite(EMOTION_MODE_LED, HIGH);
  delay(delayDuration);
  digitalWrite(EMOTION_MODE_LED, LOW);
  delay(delayDuration);

  digitalWrite(ANECDOTE_MODE_LED, HIGH);
  delay(delayDuration);
  digitalWrite(ANECDOTE_MODE_LED, LOW);
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

  playSound(1200, delayDuration);
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
  }
}

void setGameStateLedColor(int state){
  setGameStateLedColor(state, state, state);
}

void setGameStateLedColor(int r, int g, int b){
  analogWrite(GAME_STATE_LED_RED, r);
  analogWrite(GAME_STATE_LED_GREEN, g);
  analogWrite(GAME_STATE_LED_BLUE, b);
}

void onButtonThemeClick(Theme theme){
  currentTheme = theme;

  switch(currentTheme){
    case EMOTION:
      digitalWrite(ANECDOTE_MODE_LED, LOW);
      digitalWrite(EMOTION_MODE_LED, HIGH);
      break;
    case ANECDOTE:
      digitalWrite(EMOTION_MODE_LED, LOW);
      digitalWrite(ANECDOTE_MODE_LED, HIGH);
      break;
  }

  Serial.print("Mode=");
  Serial.println(themeToString(currentTheme));
}

String themeToString(Theme theme) {
  return themeLabels[theme];
}

String gameStateToString(GameState gameMode) {
  return gameStateLabels[gameMode];
}

Theme stringToTheme(String input){
  if(input == "EMOTION"){
    return EMOTION;
  }

  if(input == "ANECDOTE"){
    return ANECDOTE;
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

// Set game state and write "SetGameState={newGameState}" to serial
void setGameState(GameState newGameState){
  gameState = newGameState;
  
  Serial.print("SetGameState=");
  Serial.println(gameStateToString(gameState));
}

// Handle new game state sent by the server
void onGameStateChanged(GameState newGameState){
  if(newGameState == gameState){
    return;
  }

  switch(newGameState){
    case STARTED:
      playSound(1000, 150);
      delay(150);
      playSound(1000, 150);
      delay(150);
      playSound(1000, 150);
      delay(150);
      playSound(1500, 600);

      setGameStateLedColor(255);

      break;
    case ERROR:
      if(gameState == START || gameState == STOP){
        gameState = STOPPED;
      }

      break;
    case STOPPED:
      setGameStateLedColor(0);
      playSound(500, 1500);

      break;
  }

  if(newGameState != ERROR){
    gameState = newGameState;
  }
}

void playButtonClickSound(){
  playSound(1000, 200);
}

void playSound(int toneHz, int msDuration){
  tone(SPEAKER_PIN, toneHz, msDuration);
}