import 'package:anecdotfun/src/core/models/player.dart';
import 'package:flutter/material.dart';

import '../utils/constants.dart';
import '../services/web_socket_connection.dart';

class Game extends ChangeNotifier {
  /// Private constructor to enforce singleton pattern.
  Game._internal();

  /// The shared instance of the game state.
  static final Game _instance = Game._internal();

  factory Game() => _instance;

  /// The WebSocket connection for the game.
  late final WebSocketConnection _webSocketConnection;

  /// Initializes the WebSocket connection.
  void initializeWebSocketConnection(WebSocketConnection connection) {
    _webSocketConnection = connection;
  }

  /// The ID of the box.
  int boxId = -1;

  String username = "";

  bool stickExploded = false; // Means player is the speaker this round

  /// The unique ID of the player.
  String uniqueId = "";

  String annecdotTellerId = "";

  ValueNotifier<GameMode> mode = ValueNotifier(GameMode.theme);

  /// The current state of the game.
  ValueNotifier<GameState> state = ValueNotifier(GameState.stopped);

  /// The theme of the game
  String subject = "not yet selected";

  /// The current players in the game.
  /// Key: Player's unique ID, Value: Player object
  final ValueNotifier<Map<String, Player>> players = ValueNotifier({});

  /// Updates the current state of the game.
  void updateState(GameState newState) {
    state.value = newState;
  }

  /// Updates the current mode of the game.
  void updateMode(GameMode newMode) {
    mode.value = newMode;
  }

  /// Updates the subject of the game.
  void updateSubject(String newSubject) {
    subject = newSubject;
    notifyListeners();
  }

  /// Adds a player to the game.
  void addPlayer(String uniqueId, String username) {
    var player = Player(username: username);
    player.addListener(_onPlayerChanged);
    players.value[uniqueId] = player;
    players.notifyListeners();
  }

  void _onPlayerChanged(){
    players.notifyListeners();
  }

  /// Removes a player from the game.
  void removePlayer(String uniqueId) {
    var player = players.value[uniqueId];
    if(player == null){
      return;
    }

    player.removeListener(_onPlayerChanged);
    players.value.remove(uniqueId);
    players.notifyListeners();
  }

  /// Updates the vote status of a player.
  /// If the vote is from the server, it won't be sent back to the server.
  void updateVote(String uniqueId, String vote, {bool isFromServer = false}) {
    if (players.value.containsKey(uniqueId)) {
      players.value[uniqueId]!.updateVote(vote);
      // Only send to WebSocket if the vote originates from the app
      if (!isFromServer) {
        _webSocketConnection.vote(vote, true);
      }

      players.notifyListeners();
    }
  }

  void resetPlayersVote(){
    for(var uniqueId in players.value.keys){
      players.value[uniqueId]!.updateVote(null);
    }

    players.notifyListeners();
  }

  /// Updates the score of players
  void updateScores(int score, String speakerId) {
    players.value[speakerId]!.setScore(score);
    players.notifyListeners();
    updateState(GameState.scores);
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
    updateState(GameState.connected);
    notifyListeners();
  }

  /// Restores the game state from a saved state.
  void restoreGameState(Map<String, dynamic> state) {
    username = state['username'];
    players.value.clear();
    state['players'].forEach((key, value) {
      players.value[key] = Player.fromMap(value);
    });
    stickExploded = state['stickExploded'] as bool;
    annecdotTellerId = state['annecdotTellerId'] as String;
    this.state.value = state['state'];
    notifyListeners();
  }

  /// Indicates if the game is in the process of reconnecting.
  bool _isReconnecting = false;

  bool get isReconnecting => _isReconnecting;

  void setReconnecting(bool reconnecting) {
    _isReconnecting = reconnecting;
    notifyListeners();
  }

  /// Checks if the current player is the winner (highest score)
  bool isWinner() {
    if (!players.value.containsKey(uniqueId)) {
      return false; // The player doesn't exist in the game
    }
    final int currentPlayerScore = players.value[uniqueId]!.score;

    // Check if there is any player with a higher score
    for (var player in players.value.values) {
      if (player.score > currentPlayerScore) {
        return false;
      }
    }
    return true; // Current player has the highest score
  }

}
