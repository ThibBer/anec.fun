import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';

class PlayerScoreController {
  final WebSocketConnection webSocketConnection;
  final Game game;

  PlayerScoreController({
    required this.webSocketConnection,
    required this.game,
  });
}
