#define PIN_BUTTON_MODE_EMOTION 8
#define PIN_BUTTON_MODE_ANECDOTE 9
#define EMOTION_MODE_LED 11
#define ANECDOTE_MODE_LED 10

#define PIN_BUTTON_START_GAME 12

enum Theme {EMOTION, ANECDOTE, THEME};
enum GameState {STARTED, STOPPED};

Theme currentTheme = EMOTION;

bool isGameStarted = false;

unsigned long currentTime = 0;
unsigned long previousTime = 0;

String themeToString(Theme theme);
String gameStateToString(GameState gameMode);
void onButtonThemeClick(Theme theme);
void processSerialInput();
void startGame();
void stopGame();

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(PIN_BUTTON_MODE_EMOTION, INPUT);
  pinMode(PIN_BUTTON_MODE_ANECDOTE, INPUT);
  pinMode(PIN_BUTTON_START_GAME, INPUT);

  pinMode(EMOTION_MODE_LED, OUTPUT);
  pinMode(ANECDOTE_MODE_LED, OUTPUT);

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

  if(currentTime - previousTime >= 200){
    int startGameButton = digitalRead(PIN_BUTTON_START_GAME);

    if(startGameButton == HIGH){
      startGame();
    }

    previousTime = currentTime;
  }
}

void processSerialInput(){
  if (Serial.available() > 0) {
    String str = Serial.readString();
    str.trim();
    Serial.println(str);
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
  switch (theme) {
    case EMOTION:
      return "EMOTION";
    case ANECDOTE:
      return "ANECDOTE";
    case THEME:
      return "THEME";
    default:
      return "UNKNOWN";
  }
}

String gameStateToString(GameState gameMode) {
  switch (gameMode) {
    case EMOTION:
      return "EMOTION";
    case ANECDOTE:
      return "ANECDOTE";
    case THEME:
      return "THEME";
    default:
      return "UNKNOWN";
  }
}

void startGame(){
  if(isGameStarted){
    stopGame();
  }

  Serial.println("GameState=STARTED");
}

void stopGame(){
  Serial.println("GameState=STOPPED");
}
