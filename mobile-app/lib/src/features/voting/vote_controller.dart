import 'package:flutter/material.dart';
import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';

class VoteController with ChangeNotifier {
  final WebSocketConnection webSocketConnection;
  final Game game;

  VoteController({
    required this.webSocketConnection,
    required this.game,
  }) {
    initialize();
  }

  void initialize() {
    webSocketConnection.markVotingPageReady();
  }

  void submitVote(String uniqueId, String vote) {
    game.updateVote(uniqueId, vote);
  }
}
