import '../../core/models/game.dart';

class LeaderboardController {
  final Game game;

  LeaderboardController({required this.game});

  // Get sorted players by scores in descending order
  List<MapEntry<String, Map<String, dynamic>>> get sortedPlayers {
    return game.players.entries.toList()
      ..sort((a, b) => (game.playerScores[b.key] ?? 0)
          .compareTo(game.playerScores[a.key] ?? 0));
  }

  // Get a player's score by their unique ID
  int getPlayerScore(String uniqueId) {
    return game.playerScores[uniqueId] ?? 0;
  }
}
