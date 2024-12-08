import 'package:anecdotfun/src/core/utils/constants.dart';
import '../core/models/game.dart';

void setupMockGame() {
  final mockGame = Game();
  mockGame.boxId = 123;

  // Add mock players
  mockGame.addPlayer('player1', 'Alice');
  mockGame.addPlayer('player2', 'Bob');
  mockGame.addPlayer('player3', 'Charlie');

  // Update scores directly through the Player class
  mockGame.players.value['player1']!.score = 10;
  mockGame.players.value['player2']!.score = 15;
  mockGame.players.value['player3']!.score = 5;

  // Update game state
  mockGame.updateState(GameState.scores);
}
