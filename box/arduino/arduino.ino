#define PIN_BUTTON_MODE_EMOTION 8
#define PIN_BUTTON_MODE_ANECDOTE 9
#define EMOTION_MODE_LED 11
#define ANECDOTE_MODE_LED 10
#define GAME_STATE_LED_RED 5
#define GAME_STATE_LED_GREEN 6
#define GAME_STATE_LED_BLUE 7

#define PIN_BUTTON_START_GAME 12

enum Theme {EMOTION, ANECDOTE};
enum GameState {START, STOP, STARTED, STOPPED};

const String gameStateLabels[] = {"START", "STOP", "STARTED", "STOPPED"};
const String themeLabels[] = {"EMOTION", "ANECDOTE"};

Theme currentTheme = EMOTION;
GameState gameState = STOPPED;

unsigned long currentTime = 0;
unsigned long previousTimeButton = 0;

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
void setGameStateLedColor(int state);
void setGameStateLedColor(int r, int g, int b);

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(PIN_BUTTON_MODE_EMOTION, INPUT);
  pinMode(PIN_BUTTON_MODE_ANECDOTE, INPUT);
  pinMode(PIN_BUTTON_START_GAME, INPUT);

  pinMode(EMOTION_MODE_LED, OUTPUT);
  pinMode(ANECDOTE_MODE_LED, OUTPUT);

  pinMode(GAME_STATE_LED_RED, OUTPUT);
  pinMode(GAME_STATE_LED_GREEN, OUTPUT);
  pinMode(GAME_STATE_LED_BLUE, OUTPUT);

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

  if(currentTime - previousTimeButton >= 500){
    int requestGameStartButton = digitalRead(PIN_BUTTON_START_GAME);

    if(requestGameStartButton == HIGH){
      if(gameState == STOPPED){
        requestGameStart();
      }else if(gameState == STARTED){
        requestGameStop();
      }
    }

    previousTimeButton = currentTime;
  }
}

void blinkStartLED(int count){
  for (int i = 0; i < count; i++) {
    setGameStateLedColor(HIGH);
    delay(200);
    setGameStateLedColor(LOW);
    delay(200);
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

void setGameStateLedColor(int state){
  setGameStateLedColor(state, state, state);
}

void setGameStateLedColor(int r, int g, int b){
  digitalWrite(GAME_STATE_LED_RED, r);
  digitalWrite(GAME_STATE_LED_GREEN, g);
  digitalWrite(GAME_STATE_LED_BLUE, b);
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
    setGameStateLedColor(HIGH);
  }else{
    setGameStateLedColor(LOW);
  }

  gameState = newGameState;
}
