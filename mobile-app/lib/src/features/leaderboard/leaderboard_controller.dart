import 'package:anecdotfun/src/core/models/player.dart';

import '../../core/models/game.dart';

class LeaderboardController {
  final Game game;

  LeaderboardController({required this.game});

  /// Get sorted players by scores in descending order
  List<Player> get sortedPlayers {
    return game.players.value.values.toList()
      ..sort((a, b) => b.score.compareTo(a.score));
  }

  /// Get a player's score by their unique ID
  int getPlayerScore(String uniqueId) {
    return game.players.value[uniqueId]?.score ?? 0;
  }
}
