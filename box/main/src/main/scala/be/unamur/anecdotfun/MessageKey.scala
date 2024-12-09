package be.unamur.anecdotfun

// Used for serial interactions
object MessageKey:
  val InitFinished = "InitFinished"
  val GameMode = "GameMode"
  val SetGameState = "SetGameState"
  val GameStateChanged = "GameStateChanged"
  val HandDetected = "HandDetected"
  val VoiceFlowStart = "VoiceFlowStart"
  val RequestShutdown = "RequestShutdown"
  val GameModeChanged = "GameModeChanged"
  val StickExploded = "StickExploded"
  val StickScanned = "StickScanned"
  val RequestWsConn = "RequestWsConn"
