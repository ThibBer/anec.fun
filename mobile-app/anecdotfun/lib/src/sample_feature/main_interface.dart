import 'package:flutter/material.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import '../settings/settings_view.dart';
import 'command.dart';
import 'game.dart';
import 'dart:convert';

class MainInterface extends StatefulWidget {
  static const routeName = '/';

  const MainInterface({super.key});

  @override
  State<MainInterface> createState() => _BirdState();
}

class WebSocketConnection {
  int box_id = 0;
  Game game = Game();
  late WebSocketChannel channel;

  WebSocketConnection(String value) {
    print("init");
    box_id = int.parse(value);
    init();
  }

  void init() {
    channel =
        WebSocketChannel.connect(Uri.parse('ws://localhost:8080/ws/$box_id'));

    // Listen for messages from the server
    channel.stream.listen((message) {
      print('Received: $message');
      handleMessage(message);
    }, onError: (error) {
      print('Error: $error');
    }, onDone: () {
      print('Connection closed');
    });
  }

  void handleMessage(String message) {
    final json = jsonDecode(message);
    final command = Command.fromJson(json);

    if (command is StartGameCommand) {
      game.updateState(GameState.STARTED);
    } else if (command is StopGameCommand) {
      game.updateState(GameState.STOPPED);
    } else if (command is VoteCommand) {
      game.updateState(GameState.VOTING);
    } else if (command is ConnectRemote) {
      print("Remote connected");
    } else if (command is DisconnectRemote) {
      print("Remote disconnected");
    } else {
      print("Unknown command");
    }
    // Add handlers for each command type
  }

  void connect() {
    channel.sink.add(jsonEncode(
        {"box_id": 2, "remote_id": 1, "commandType": "ConnectRemote"}));
  }

  void diconnect() {
    channel.sink.add(jsonEncode(
        {"box_id": 2, "remote_id": 1, "commandType": "DisconnectRemote"}));
  }

  void vote() {
    channel.sink
        .add(jsonEncode({"box_id": 2, "remote_id": 1, "VoteCommand": "true"}));
  }

  void close() {
    channel.sink.close();
  }
}

class _BirdState extends State<MainInterface> {
  double _size = 1.0;
  final _email = '';
  final _formKey = GlobalKey<FormState>();
  late WebSocketConnection _webSocketConnection;

  void grow() {
    setState(() {
      _size += 0.1;
    });
  }

  void _submitForm() {
    if (_formKey.currentState!.validate()) {
      _formKey.currentState!.save();
      // Now you can use _webSocketConnection to connect
      _webSocketConnection.connect();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cast your vote'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              Navigator.restorablePushNamed(context, SettingsView.routeName);
            },
          ),
        ],
      ),
      body: Form(
        key: _formKey,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Flexible(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: TextFormField(
                  decoration: const InputDecoration(labelText: 'Box id'),
                  onSaved: (value) {
                    _webSocketConnection = WebSocketConnection(value!);
                  },
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return 'Please enter your box id';
                    }
                    return null;
                  },
                ),
              ),
            ),
            const SizedBox(width: 20),
            ElevatedButton(
              onPressed: _submitForm,
              child: const Text('Submit'),
            ),
          ],
        ),
      ),
    );
  }
}
