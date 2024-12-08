import 'dart:async';
import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';
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
    String? username,
  }) {
    if (boxId != null) {
      _instance.game.boxId = boxId;
    }
    if (username != null) {
      _instance.game.username = username;
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

  late Timer _heartbeatTimer;

  void init(String? uniqueId) async {
    game.setError(""); //Reset error message
    game.setConnecting(true);

    try {
      var baseUrl = 'wss://anecdotfun2.vsantele.dev/ws/${game.boxId}';
      var uri =
          Uri.parse(uniqueId == null ? baseUrl : "$baseUrl?uniqueId=$uniqueId");
      print("WebSocket URI : $uri");

      channel = WebSocketChannel.connect(uri);
      await channel.ready;

      // Listen for messages from the server
      channel.stream.listen(
        (message) => _handleMessage(message),
        onError: (error) {
          game.setError("Connection error: $error");
          game.setConnecting(false);
          _reconnect(uniqueId); // Attempt to reconnect
        },
        onDone: () {
          if (channel.closeCode != null && channel.closeCode != 1000) {
            game.setError("Connection closed, trying to reconnect");
            _reconnect(uniqueId); // Reconnect only if the closure was abnormal
          }
        },
      );

      _heartbeatTimer = Timer.periodic(Duration(seconds: 20), (timer) {
        if (channel.closeCode != null) {
          _heartbeatTimer.cancel();
          return;
        }

        try {
          channel.sink.add("heartbeat");
        } catch (error) {
          game.setError("Failed to send heartbeat");
        }
      });

      retrieveState();
    } on WebSocketChannelException catch (_) {
      game.setError("Server unavailable, retry later");
      game.setConnecting(false);
    } catch (error) {
      game.setError("Failed to initialize connection: $error");
      game.setConnecting(false);

      _reconnect(uniqueId); // Attempt to reconnect
    }
  }

  int _retryCount = 0;
  final int _maxRetries = 5;

  void _reconnect(String? uniqueId) {
    print("Try to reconnect to web socket");
    if (_retryCount >= _maxRetries) {
      game.setError("Failed to reconnect after $_maxRetries attempts.");
      return;
    }

    Future.delayed(Duration(seconds: 2 * _retryCount), () {
      _retryCount++;
      init(uniqueId); // Reinitialize the connection
    });
  }

  /// Handles incoming messages from the WebSocket connection.
  ///
  /// This function is called for each message received from the server. It
  /// parses the message as JSON and creates a [Command] object from the
  /// parsed JSON. It then delegates the handling of the command to the
  /// appropriate method.
  void _handleMessage(String message) async {
    print("Received : $message");
    final json = jsonDecode(message);
    final command = Command.fromJson(json, game.boxId);

    if (command is Connection) {
      if (game.uniqueId.isEmpty) {
        game.setSuccess("Connected to server successfully!");
        game.uniqueId = command.uniqueId;
        connect();
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
      } else if (command.message == 'ROUND_STARTED') {
        game.updateState(GameState.roundStarted);
      } else if (command.message == 'STICK_EXPLODED') {
        game.updateState(GameState.stickExploded);
      } else if (command.message == 'STOPPED') {
        game.updateState(GameState.stopped);
      } else if (command.message == 'ROUND_STOPPED') {
        game.resetPlayersVote();
        game.updateState(GameState.roundStopped);
      }
    } else if (command is StickExploded) {
      game.stickExploded = true;
    } else if (command is VoteResult) {
      game.updateScores(int.parse(json['message']), json['senderUniqueId']);
    } else if (command is AnnecdotTeller) {
      game.annecdotTellerId = json['senderUniqueId'];
    } else if (command is RetrieveStateCommand) {
      game.restoreGameState(json);
    } else if (command is GameModeChanged) {
      game.updateMode(GameMode.values.byName(command.gameMode.toLowerCase()));
    } else if (command is ClientDisconnected) {
      game.removePlayer(command.disconnectedUserId);
    } else if (command is SubjectChanged) {
      game.updateSubject(command.subject);
    } else {
      game.setError("Unknown command received: $command");
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
    if (status == 'failed') {
      game.setError(response['message']);
      game.updateState(GameState.disconnected);
      game.uniqueId = "";
      game.boxId = -1;
      close(
          1000); // close connection with code 1000, it's a normal behavior to close websocket connection if connect response is failed
    } else {
      if (response['senderUniqueId'] == game.uniqueId) {
        game.setSuccess("Remote connected");
        game.updateState(GameState.connected);
        print("Remote connected");
        if (!votingPageReadyCompleter.isCompleted) {
          await votingPageReadyCompleter.future;
        }
        // Save our unique ID and timestamp on disk
        final prefs = await SharedPreferences.getInstance();
        var connectionSettings = [
          game.boxId.toString(),
          game.uniqueId,
          DateTime.now().toString()
        ];
        await prefs.setStringList("connectionSettings", connectionSettings);

        print("Save uniqueId to SharedPreferences : $connectionSettings");
      }

      game.addPlayer(response['senderUniqueId'], response['message']);
    }

    game.setConnecting(false);
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
      game.setError(response['message']);
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
      if (game.uniqueId == response['uniqueId']) {
        game.updateState(GameState.disconnected);
        cleanup();
      } else {
        game.removePlayer(game.uniqueId);
      }
    } else {
      game.setError(response['message']);
    }
  }

  void cleanup() {
    try {
      close(1000); // Close the WebSocket connection with a normal closure code
      _heartbeatTimer.cancel();
    } catch (error) {
      game.setError("Cleanup failed: $error");
    }
    game.reset();
  }

  void sendCommand(Map<String, dynamic> commandData) {
    try {
      channel.sink.add(jsonEncode(commandData));
    } catch (error) {
      game.setError("Failed to send command: $error");
    }
  }

  void connect() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "ConnectRemote",
      "username": game.username
    });
  }

  void disconnect() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "DisconnectRemote",
    });
    print("Disconnect command sent");
  }

  void vote(String vote, bool speaker) {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "vote": vote,
      "isSpeaker": speaker,
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

  void sendStickScanned() {
    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "ScannedStickCommand",
    });
  }

  void retrieveState() {
    print("retrieveState disabled because server is not ready yet");

/*    sendCommand({
      "boxId": game.boxId,
      "uniqueId": game.uniqueId,
      "commandType": "RetrieveStateCommand",
    });*/
  }

  void close(int? code) {
    try {
      if (code != null) {
        channel.sink.close(code);
      } else {
        channel.sink.close();
      }

      _heartbeatTimer.cancel();
    } catch (error) {
      game.setError("Failed to close connection: $error");
    }
  }
}
