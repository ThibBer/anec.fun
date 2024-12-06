import 'package:anecdotfun/src/core/models/game.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../core/services/web_socket_connection.dart';
import 'package:flutter/material.dart';

/// A controller class to manage the connection logic of the connection page.
///
/// This class handles form validation, WebSocket initialization, and disposal of resources.
class ConnectionController {
  late final Game game;
  ConnectionController({required this.game});

  /// A key to identify the form and access its state.
  final GlobalKey<FormState> formKey = GlobalKey<FormState>();

  /// Controller for the box ID text field.
  final TextEditingController boxIdController = TextEditingController();

  /// Controller for the name text field.
  final TextEditingController nameController = TextEditingController();

  WebSocketConnection? _webSocketConnection;

  /// Initializes the WebSocket connection using the provided box ID.
  ///
  /// [boxId]: The ID of the box to connect to.
  /// [onSuccess]: Callback to invoke upon successful connection.
  /// [onError]: Callback to invoke upon encountering an error.
  void initializeWebSocket(String boxId, String username, {reconnect = false}) {
    _webSocketConnection = WebSocketConnection(
      boxId: int.parse(boxId),
      username: username,
    );
    _webSocketConnection?.init(reconnect);
  }

  /// Submits the form and attempts to establish a WebSocket connection.
  ///
  /// [onSuccess]: Callback to invoke upon successful connection.
  /// [onError]: Callback to invoke upon encountering an error.
  void submitForm() {
    // Check name is valid
    if (nameController.text.trim().isEmpty) {
      game.setError("Please enter your name");
      return;
    }
    if (formKey.currentState!.validate()) {
      formKey.currentState!.save();
      initializeWebSocket(
          boxIdController.text.trim(), nameController.text.trim());
    }
  }

  void reconnect() async {
    final prefs = await SharedPreferences.getInstance();
    var uniqueId, _ = prefs.getStringList('uniqueId');
    initializeWebSocket(uniqueId, "", reconnect: true);
    game.setReconnecting(true);
  }

  /// Disposes the controller, releasing any resources held.
  void disposeController() {
    boxIdController.dispose();
    _webSocketConnection?.close();
  }

  Future<bool> promptReconnect() async {
    // Check if stored unique ID is available and not too old
    final prefs = await SharedPreferences.getInstance();
    var _, timestamp = prefs.getStringList('uniqueId');
    // check if timestamp is not older than 15 minutes
    if (timestamp != null &&
        DateTime.now().difference(DateTime.parse(timestamp[1])).inMinutes <
            15) {
      return true;
    }
    return false;
  }
}
