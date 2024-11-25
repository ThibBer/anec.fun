import 'package:anecdotfun/src/core/models/game.dart';

import '../../core/services/web_socket_connection.dart';
import 'package:flutter/material.dart';

class StickPassingController with ChangeNotifier {
  late final Game game;
  late final WebSocketConnection webSocketConnection;
  StickPassingController({
    required this.webSocketConnection,
    required this.game,
  });

  validateStick() {
    return game.stickExploded;
  }

  void sendVotingState() {
    webSocketConnection.votingStarted();
  }

  void onScanSuccessful() {
    webSocketConnection.sendStickScanned();
  }

  disposeController() {
    super.dispose();
  }
}
