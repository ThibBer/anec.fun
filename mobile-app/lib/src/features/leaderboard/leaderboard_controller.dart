import 'package:anecdotfun/src/core/models/player.dart';
import 'package:anecdotfun/src/core/services/web_socket_connection.dart';
import 'package:anecdotfun/src/core/utils/constants.dart';

import '../../core/models/game.dart';

class LeaderboardController {
  final Game game;

  LeaderboardController({required this.game});
  WebSocketConnection webSocketConnection = WebSocketConnection();
  /// Get sorted players by scores in descending order
  List<Player> get sortedPlayers {
    return game.players.value.values.toList()
      ..sort((a, b) => b.score.compareTo(a.score));
  }

  /// Get a player's score by their unique ID
  int getPlayerScore(String uniqueId) {
    return game.players.value[uniqueId]?.score ?? 0;
  }

  int getPlayerRank(String uniqueId) {
    final sortedPlayers = game.players.value.entries.toList()
      ..sort((a, b) =>
          b.value.score.compareTo(a.value.score)); // Sort by score descending
    for (int i = 0; i < sortedPlayers.length; i++) {
      if (sortedPlayers[i].key == uniqueId) {
        return i + 1; // Rank is 1-based
      }
    }
    return -1; // Player not found
  }
  void disconnect() {
    game.updateState(GameState.disconnecting);
    webSocketConnection.disconnect();
  }
}
