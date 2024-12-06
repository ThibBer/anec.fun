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

  // Maintain score of each player
  final Map<String, int> playerScores = {};

  /// The ID of the box.
  int boxId = -1;

  String username = "";

  bool stickExploded = false; // Means player is speaker this round

  /// The unique ID of the player.
  String uniqueId = "";

  String annecdotTellerId = "";

  GameMode mode = GameMode.theme;

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

  /// Updates the current mode of the game.
  void updateMode(GameMode newMode) {
    mode = newMode;
    notifyListeners();
  }

  /// Adds a player to the game.
  void addPlayer(String uniqueId, String username) {
    players[uniqueId] = {
      "username": username,
      "vote": null
    }; // Initially, no vote
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
        _webSocketConnection.vote(vote, true);
      }
      notifyListeners();
    }
  }

  void updateScores(String result) {
    // Update each player's score based on the result and his vote
    players.forEach((uniqueId, playerData) {
      if (playerData["vote"] == result) {
        playerScores[uniqueId] = (playerScores[uniqueId] ?? 0) + 1;
      }
    });
    updateState(GameState.scores);
    notifyListeners();
  }

  /// Error message to be displayed across the app.
  String? error;

  /// Updates the error property and notifies listeners.
  void setError(String? message) {
    error = message;
    notifyListeners();
  }

  /// Success message to be displayed across the app.
  String? success;

  /// Updates the success property and notifies listeners.
  void setSuccess(String? message) {
    success = message;
    notifyListeners();
  }

  /// Connection status of the WebSocket.
  bool _isConnecting = false;

  bool get isConnecting => _isConnecting;

  void setConnecting(bool connecting) {
    _isConnecting = connecting;
    notifyListeners();
  }

  bool connected = false;

  void setConnected(bool connected) {
    this.connected = connected;
    notifyListeners();
  }

  void restoreGameState(Map<String, dynamic> state) {
    username = state['username'];
    players.clear();
    state['players'].forEach((key, value) {
      players[key] = Map<String, dynamic>.from(value);
    });
    playerScores.clear();
    state['playerScores'].forEach((key, value) {
      playerScores[key] = value as int;
    });
    stickExploded = state['stickExploded'] as bool;
    annecdotTellerId = state['annecdotTellerId'] as String;
    state = state['state'];
    notifyListeners();
  }

  /// Indicates if the game is in the process of reconnecting.
  bool _isReconnecting = false;

  bool get isReconnecting => _isReconnecting;

  void setReconnecting(bool reconnecting) {
    _isReconnecting = reconnecting;
    notifyListeners();
  }
}
