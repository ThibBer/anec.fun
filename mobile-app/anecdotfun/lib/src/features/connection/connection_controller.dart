import '../../core/services/web_socket_connection.dart';
import 'package:flutter/material.dart';

/// A controller class to manage the connection logic of the connection page.
///
/// This class handles form validation, WebSocket initialization, and disposal of resources.
class ConnectionController with ChangeNotifier {
  /// A key to identify the form and access its state.
  final GlobalKey<FormState> formKey = GlobalKey<FormState>();

  /// Controller for the box ID text field.
  final TextEditingController boxIdController = TextEditingController();

  WebSocketConnection? _webSocketConnection;

  bool _isConnecting = false;
  String? _connectionMessage;

  /// Whether the controller is currently attempting to connect.
  bool get isConnecting => _isConnecting;

  /// Message related to the current connection status.
  String? get connectionMessage => _connectionMessage;

  /// Initializes the WebSocket connection using the provided box ID.
  ///
  /// [boxId]: The ID of the box to connect to.
  /// [onSuccess]: Callback to invoke upon successful connection.
  /// [onError]: Callback to invoke upon encountering an error.
  void initializeWebSocket(
    String boxId, {
    required void Function() onSuccess,
    required void Function(String) onError,
  }) {
    _isConnecting = true;
    _connectionMessage = null;
    notifyListeners();
    try {
      _webSocketConnection = WebSocketConnection(
        boxId: int.parse(boxId),
        onError: (error) {
          _connectionMessage = "Error: $error";
          _isConnecting = false;
          notifyListeners();
          onError(error);
        },
        onMessage: (message) {
          _connectionMessage = "Received: $message";
          notifyListeners();
        },
        onConnectionClosed: (message) {
          _connectionMessage = "Connection closed: $message";
          _isConnecting = false;
          notifyListeners();
        },
        onConnectionSuccess: (message) {
          _webSocketConnection?.connect();
          _connectionMessage = "Connected successfully: $message";
          _isConnecting = false;
          notifyListeners();
        },
        onRemoteConnectionSuccess: (message) {
          _connectionMessage = "Remote connection success: $message";
          _isConnecting = false;
          notifyListeners();
          onSuccess();
        },
      );
      _webSocketConnection?.init();
    } catch (error) {
      _connectionMessage = "Failed to connect: $error";
      _isConnecting = false;
      notifyListeners();
      onError(error.toString());
    }
  }

  /// Submits the form and attempts to establish a WebSocket connection.
  ///
  /// [onSuccess]: Callback to invoke upon successful connection.
  /// [onError]: Callback to invoke upon encountering an error.
  void submitForm({
    required void Function() onSuccess,
    required void Function(String) onError,
  }) {
    if (formKey.currentState!.validate()) {
      formKey.currentState!.save();
      initializeWebSocket(boxIdController.text.trim(),
          onSuccess: onSuccess, onError: onError);
    }
  }

  /// Disposes the controller, releasing any resources held.
  void disposeController() {
    boxIdController.dispose();
    _webSocketConnection?.close();
  }
}
