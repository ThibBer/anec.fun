import 'dart:async';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';
import '../models/command.dart';
import '../models/game.dart';
import '../utils/constants.dart';

/// A singleton class that manages the WebSocket connection to the server.
///
/// This class has a factory constructor that takes a box ID and callback
/// functions for handling errors, messages, connection closure, connection
/// success, and remote connection success.
///
/// The class also has methods for sending commands to the server, connecting to
/// the server, disconnecting from the server, voting, and closing the
/// connection.
class WebSocketConnection {
  static final WebSocketConnection _instance = WebSocketConnection._internal();

  factory WebSocketConnection({
    int? boxId,
    void Function(String)? onError,
    void Function(String)? onMessage,
    void Function(String)? onConnectionClosed,
    void Function(String)? onConnectionSuccess,
    void Function(String)? onRemoteConnectionSuccess,
  }) {
    if (boxId != null) {
      _instance.game.boxId = boxId;
    }
    if (onError != null) _instance.onError = onError;
    if (onMessage != null) _instance.onMessage = onMessage;
    if (onConnectionClosed != null) {
      _instance.onConnectionClosed = onConnectionClosed;
    }
    if (onConnectionSuccess != null) {
      _instance.onConnectionSuccess = onConnectionSuccess;
    }
    if (onRemoteConnectionSuccess != null) {
      _instance.onRemoteConnectionSuccess = onRemoteConnectionSuccess;
    }

    return _instance;
  }

  WebSocketConnection._internal() {
    game.initializeWebSocketConnection(this);
  }

  /// A completer that is used to notify the connection controller when the
  /// voting page is ready.
  final Completer<void> votingPageReadyCompleter = Completer<void>();

  /// The game object that the connection is associated with.
  ///
  /// The game object is used to manage the game state and communicate with the
  /// WebSocket connection.
  Game game = Game();

  /// The WebSocket channel that is used for communication.
  late WebSocketChannel channel;

  // Callback functions for handling WebSocket events
  late void Function(String) onError;
  late void Function(String) onMessage;
  late void Function(String) onConnectionClosed;
  late void Function(String) onConnectionSuccess;
  late void Function(String) onRemoteConnectionSuccess;

  void init() {
    game.boxId = game.boxId;
    try {
      channel = WebSocketChannel.connect(
          Uri.parse('ws://10.0.2.2:8080/ws/${game.boxId}'));

      // Listen for messages from the server
      channel.stream.listen((message) {
        _handleMessage(message);
      }, onError: (error) {
        onError(error.toString());
      }, onDone: () {
        onConnectionClosed("Connection closed by server.");
      });
    } catch (error) {
      onError("Failed to initialize connection: $error");
    }
  }

  /// Handles incoming messages from the WebSocket connection.
  ///
  /// This function is called for each message received from the server. It
  /// parses the message as JSON and creates a [Command] object from the
  /// parsed JSON. It then delegates the handling of the command to the
  /// appropriate method.
  void _handleMessage(String message) async {
    final json = jsonDecode(message);
    print("Received message: $json");
    final command = Command.fromJson(json, game.boxId);

    if (command is Connection) {
      if (game.uniqueId.isEmpty) {
        onConnectionSuccess("Connected to server successfully!");
        game.uniqueId = command.uniqueId;
      }
    } else if (command is VoteCommand) {
      _handleVoteResponse(json);
    } else if (command is ConnectRemote) {
      _handleConnectResponse(json);
    } else if (command is DisconnectRemote) {
      _handleDisconnectResponse(json);
    } else if (command is StatusCommand) {
      if (command.message == 'VOTING') {
        game.updateState(GameState.voting);
      }
     else if (command.message == 'STARTED') {
        game.updateState(GameState.started);
      }
    } else if (command is StickExploded) {
      game.stickExploded = true;
    } else {
      onError("Unknown command received: $command");
    }
  }

  /// Handles the connection response from the server.
  ///
  /// If the status is 'success' and the sender's unique ID matches the game's
  /// unique ID, it triggers the remote connection success callback and waits for
  /// the voting page to be ready, if not already completed. It adds the sender
  /// as a player to the game. If the status is not 'success', it triggers an
  /// error callback with the received message.
  Future<void> _handleConnectResponse(Map<String, dynamic> response) async {
    final status = response['status'];
    if (status == 'success') {
      if (response['senderUniqueId'] == game.uniqueId) {
        onRemoteConnectionSuccess("Remote connected");
        if (!votingPageReadyCompleter.isCompleted) {
          await votingPageReadyCompleter.future;
        }
      }
      game.addPlayer(response['senderUniqueId']);
    } else {
      onError(response['message']);
    }
  }

  /// Marks the voting page as ready.
  ///
  /// This should be called when the voting page is ready to receive updates
  /// from the server. It will trigger the remote connection success callback if
  /// it hasn't already been triggered.
  void markVotingPageReady() {
    if (!votingPageReadyCompleter.isCompleted) {
      votingPageReadyCompleter.complete();
    }
  }

  /// Handles a vote response from the server.
  ///
  /// If the status is 'success', it updates the vote status of the sender in
  /// the game. If the status is not 'success', it triggers an error callback
  /// with the received message.
  void _handleVoteResponse(Map<String, dynamic> response) {
    if (response['status'] == 'success') {
      game.updateVote(response['senderUniqueId'], response['message'],
          isFromServer: true);
    } else {
      onError(response['message']);
    }
  }

  /// Handles the disconnect response from the server.
  ///
  /// If the status is 'success', it removes the game's unique ID from the game.
  /// If the status is not 'success', it triggers an error callback with the
  /// received message.
  void _handleDisconnectResponse(Map<String, dynamic> response) async {
    final status = response['status'];
    if (status == 'success') {
      game.removePlayer(game.uniqueId);
    } else {
      onError(response['message']);
    }
  }

  void sendCommand(Map<String, dynamic> commandData) {
    try {
      channel.sink.add(jsonEncode(commandData));
    } catch (error) {
      onError("Failed to send command: $error");
    }
  }

  void connect() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "ConnectRemote",
    });
  }

  void disconnect() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "DisconnectRemote",
    });
  }

  void vote(String vote) {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "vote": vote,
      "commandType": "VoteCommand",
    });
  }

  void votingStarted() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "StartVoting",
    });
  }

  void close() {
    try {
      channel.sink.close();
    } catch (error) {
      onError("Failed to close connection: $error");
    }
  }
}
