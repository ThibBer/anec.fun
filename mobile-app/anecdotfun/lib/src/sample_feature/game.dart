enum GameState { STOPPED, STARTED, PAUSED, VOTING }

class Game {
  var state = GameState.STOPPED;

  Game();

  void updateState(GameState newState) {
    state = newState;
  }
}
