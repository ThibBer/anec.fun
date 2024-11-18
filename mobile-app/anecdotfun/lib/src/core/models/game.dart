import 'package:flutter/material.dart';

import '../utils/constants.dart';
import '../services/web_socket_connection.dart';

/// A single instance of the game state that is used throughout the app.
/// Manages the game state and communicates with the WebSocket connection.
class Game extends ChangeNotifier {
  /// Private constructor to enforce singleton pattern.
  Game._internal();

  /// The shared instance of the game state.
  static final Game _instance = Game._internal();

  factory Game() {
    return _instance;
  }

  /// The WebSocket connection for the game.
  late final WebSocketConnection _webSocketConnection;

  /// Initializes the WebSocket connection.
  void initializeWebSocketConnection(WebSocketConnection connection) {
    _webSocketConnection = connection;
  }

  /// The ID of the box.
  int boxId = -1;

  /// The unique ID of the player.
  String uniqueId = "";

  /// The current state of the game.
  GameState state = GameState.stopped;

  /// The current players in the game.
  /// The key is the unique ID of the player, and the value is a map that contains
  /// the vote status of the player.
  final Map<String, Map<String, dynamic>> players = {};

  /// Updates the current state of the game.
  void updateState(GameState newState) {
    state = newState;
    notifyListeners();
  }

  /// Adds a player to the game.
  void addPlayer(String uniqueId) {
    players[uniqueId] = {"vote": null}; // Initially, no vote
    notifyListeners();
  }

  /// Removes a player from the game.
  void removePlayer(String uniqueId) {
    players.remove(uniqueId);
    notifyListeners();
  }

  /// Updates the vote status of a player.
  /// If the vote is from the server, it won't be sent back to the server.
  void updateVote(String uniqueId, String vote, {bool isFromServer = false}) {
    if (players.containsKey(uniqueId)) {
      players[uniqueId]!["vote"] = vote;
      // Only send to WebSocket if the vote originates from the app
      if (!isFromServer) {
        _webSocketConnection.vote(vote);
      }
      notifyListeners();
    }
  }
}
