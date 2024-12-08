package be.unamur.anecdotfun

// Used for websocket interactions
object CommandType {
  val CONNECTION = "Connection"
  val CONNECT_BOX = "ConnectBox"
  val START_GAME = "StartGameCommand"
  val STOP_GAME = "StopGameCommand"
  val IDLE_GAME = "IdleGameCommand"
  val VOICE_FLOW = "VoiceFlow"
  val STICK_EXPLODED = "StickExploded"
  val STATUS = "StatusCommand"
  val SET_GAME_MODE = "SetGameModeCommand"
  val GAME_MODE_CHANGED = "GameModeChanged"
  val ANECDOTE_TELLER =  "AnnecdotTeller"
  val STICK_SCANNED =  "StickScanned"
}
