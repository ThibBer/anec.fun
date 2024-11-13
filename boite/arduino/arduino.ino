#define PIN_BUTTON_MODE_EMOTION 8
#define PIN_BUTTON_MODE_ANECDOTE 9
#define EMOTION_MODE_LED 11
#define ANECDOTE_MODE_LED 10
#define GAME_STARTED_LED 7

#define PIN_BUTTON_START_GAME 12

enum Theme {EMOTION, ANECDOTE};
enum GameState {START, STOP, STARTED, STOPPED};

const String gameStateLabels[] = {"START", "STOP", "STARTED", "STOPPED"};
const String themeLabels[] = {"EMOTION", "ANECDOTE"};

Theme currentTheme = EMOTION;
GameState gameState = STOPPED;

unsigned long currentTime = 0;
unsigned long previousTime = 0;

String themeToString(Theme theme);
String gameStateToString(GameState gameState);
Theme stringToTheme(String input);
GameState stringToGameState(String input);
void onButtonThemeClick(Theme theme);
void processSerialInput();
void requestGameStart();
void requestGameStop();
void setGameState(GameState newGameState);
void onGameStateChanged(GameState state);
void onReceiveSerialData(String key, String value);
void blinkStartLED(int count);

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(PIN_BUTTON_MODE_EMOTION, INPUT);
  pinMode(PIN_BUTTON_MODE_ANECDOTE, INPUT);
  pinMode(PIN_BUTTON_START_GAME, INPUT);

  pinMode(EMOTION_MODE_LED, OUTPUT);
  pinMode(ANECDOTE_MODE_LED, OUTPUT);
  pinMode(GAME_STARTED_LED, OUTPUT);

  blinkStartLED(3);
  digitalWrite(EMOTION_MODE_LED, HIGH);
}

void loop() {
  currentTime = millis();

  processSerialInput();

  int emotionButton = digitalRead(PIN_BUTTON_MODE_EMOTION);
  int anecdoteButton = digitalRead(PIN_BUTTON_MODE_ANECDOTE);

  if (emotionButton == HIGH && currentTheme != EMOTION) {
    onButtonThemeClick(EMOTION);
  } else if(anecdoteButton == HIGH && currentTheme != ANECDOTE) {
    onButtonThemeClick(ANECDOTE);
  }

  if(currentTime - previousTime >= 500){
    int requestGameStartButton = digitalRead(PIN_BUTTON_START_GAME);

    if(requestGameStartButton == HIGH){
      requestGameStart();
    }

    previousTime = currentTime;
  }
}

void blinkStartLED(int count){
  for (int i = 0; i < count; i++) {
    digitalWrite(GAME_STARTED_LED, HIGH);
    delay(500);
    digitalWrite(GAME_STARTED_LED, LOW);
    delay(500);
  }
}

void processSerialInput(){
  if (Serial.available() > 0) {
    String serialData = Serial.readString();
    serialData.trim();

    int equalIndex = serialData.indexOf('=');
    String key = serialData.substring(0, equalIndex);
    String value = serialData.substring(equalIndex + 1);

    onReceiveSerialData(key, value);
  }
}

void onReceiveSerialData(String key, String value){
  if(key == "GameStateChanged"){
    onGameStateChanged(stringToGameState(value));
  }
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

  if(input == "STOPPED"){
    return STOPPED;
  }
}

void requestGameStart(){
  setGameState(START);
}

void requestGameStop(){
  setGameState(STOP);
}

// Set game state and write "SetGameState={newGameState}" to serial
void setGameState(GameState newGameState){
  gameState = newGameState;
  
  Serial.print("SetGameState=");
  Serial.println(gameStateToString(gameState));
}

// Handle new game state sent by the server
void onGameStateChanged(GameState newGameState){
  if(newGameState == STARTED){
    digitalWrite(GAME_STARTED_LED, HIGH);
  }else{
    digitalWrite(GAME_STARTED_LED, LOW);
  }

  gameState = newGameState;
}
