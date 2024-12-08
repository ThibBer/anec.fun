import 'package:anecdotfun/src/core/models/game.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../core/services/web_socket_connection.dart';
import 'package:flutter/material.dart';

/// A controller class to manage the connection logic of the connection page.
///
/// This class handles form validation, WebSocket initialization, and disposal of resources.
class ConnectionController {
  late final Game game;
  ConnectionController({required this.game}) {
    formKey = GlobalKey<FormState>();
  }

  /// A key to identify the form and access its state.
  late GlobalKey<FormState> formKey;

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
  void initializeWebSocket(String boxId, String username, {String? uniqueId}) {
    _webSocketConnection = WebSocketConnection(
      boxId: int.parse(boxId),
      username: username,
    );
    _webSocketConnection?.init(uniqueId);
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
    var connectionSettings = prefs.getStringList("connectionSettings");
    if(connectionSettings == null || connectionSettings.length <= 2){
      return;
    }

    var boxId = connectionSettings[0];
    var uniqueId = connectionSettings[1];
    var username = connectionSettings[2];

    print("Reconnect to box $boxId with uniqueId : $uniqueId");

    initializeWebSocket(boxId, username, uniqueId: uniqueId);
    game.setReconnecting(true);
  }

  /// Disposes the controller, releasing any resources held.
  void disposeController() {
    boxIdController.dispose();
    //_webSocketConnection?.close();
  }

  Future<bool> canBeReconnected() async {
    // Check if stored unique ID is available and not too old
    final prefs = await SharedPreferences.getInstance();
    var connectionSettings = prefs.getStringList("connectionSettings");
    if(connectionSettings == null || connectionSettings.length < 4){
      return false;
    }

    var timestamp = connectionSettings[3];

    // check if timestamp is not older than 15 minutes
    return DateTime.now().difference(DateTime.parse(timestamp)).inMinutes < 15;
  }
}
