import 'package:anecdotfun/src/core/utils/constants.dart';

import '../core/models/game.dart';

void setupMockGame() {
  final mockGame = Game();
  mockGame.boxId = 123;

  // Add mock players and scores
  mockGame.addPlayer('player1', 'Alice');
  mockGame.addPlayer('player2', 'Bob');
  mockGame.addPlayer('player3', 'Charlie');

  mockGame.playerScores['player1'] = 10;
  mockGame.playerScores['player2'] = 15;
  mockGame.playerScores['player3'] = 5;

  mockGame.updateState(GameState.scores);
}
