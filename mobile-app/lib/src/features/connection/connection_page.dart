import 'package:anecdotfun/src/core/models/game.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../settings/settings_view.dart';
import '../voting/vote_page.dart';
import 'connection_controller.dart';
import 'package:lottie/lottie.dart';

class ConnectionPage extends StatefulWidget {
  static const routeName = '/';

  const ConnectionPage({super.key});

  @override
  State<ConnectionPage> createState() => _ConnectionPageState();
}

class _ConnectionPageState extends State<ConnectionPage> {
  late final ConnectionController _controller;

  @override
  void initState() {
    super.initState();
    _controller = ConnectionController(game: Game());
    _checkReconnection();
  }

  @override
  void dispose() {
    _controller.disposeController();
    super.dispose();
  }

  void _checkReconnection() async {
    bool canBeReconnected = await _controller.canBeReconnected();
    if (canBeReconnected) {
      _showReconnectDialog();
    } else {
      await _deleteSavedConnectionSettings();
    }
  }

  Future<void> _deleteSavedConnectionSettings() async {
    final prefs = await SharedPreferences.getInstance();
    prefs.remove("connectionSettings");
  }

  void _showReconnectDialog() {
    showDialog(
      context: context,
      barrierDismissible: false, // Prevent dismissing by tapping outside
      builder: (context) {
        return AlertDialog(
          title: const Text('Reconnect to game'),
          content: const Text('Do you want to reconnect to the last session?'),
          actions: [
            TextButton(
              onPressed: () {
                _deleteSavedConnectionSettings();
                Navigator.of(context).pop(); // Close the dialog
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                _controller.reconnect();
                Navigator.of(context).pop(); // Close the dialog
              },
              child: const Text('Reconnect'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
        listenable: _controller.game,
        builder: (context, _) {
          if (_controller.game.isReconnecting) {
            // Show a loading dialog or page while reconnecting
            return Scaffold(
              body: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 20),
                    const Text('Reconnecting... Please wait'),
                  ],
                ),
              ),
            );
          }
          if (_controller.game.connected) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              Navigator.pushReplacementNamed(context, VotePage.routeName);
            });
          }
          return Scaffold(
            appBar: AppBar(
              title: const Text('Connect to Box'),
              actions: [
                IconButton(
                  icon: const Icon(Icons.settings),
                  onPressed: () {
                    Navigator.restorablePushNamed(
                        context, SettingsView.routeName);
                  },
                ),
              ],
            ),
            body: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Logo
                  SvgPicture.asset(
                    'assets/images/logo_long.svg',
                    semanticsLabel: 'Dart Logo',
                  ),

                  Expanded(
                    child: Lottie.asset(
                      'assets/animations/welcome.json',
                      repeat: true,
                      renderCache: RenderCache.raster,
                      fit: BoxFit.contain,
                    ),
                  ),
                  Form(
                    key: _controller.formKey,
                    child: Column(
                      children: [
                        TextFormField(
                          controller: _controller.nameController,
                          decoration: const InputDecoration(
                            labelText: 'Your Name',
                            hintText: 'Enter your name',
                            border: OutlineInputBorder(),
                          ),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return 'Please enter your name';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 20),
                        TextFormField(
                          controller: _controller.boxIdController,
                          decoration: const InputDecoration(
                            labelText: 'Box ID',
                            hintText: 'Enter the box ID',
                            border: OutlineInputBorder(),
                          ),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return 'Please enter your box ID';
                            }
                            return null;
                          },
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  AnimatedBuilder(
                    animation: _controller.game,
                    builder: (context, child) {
                      return _controller.game.isConnecting
                          ? const CircularProgressIndicator()
                          : ElevatedButton(
                              onPressed: () {
                                _controller.submitForm();
                              },
                              child: const Text('Connect'),
                            );
                    },
                  ),
                  const SizedBox(height: 20),
                  AnimatedBuilder(
                    animation: _controller.game,
                    builder: (context, child) {
                      if (_controller.game.error != null) {
                        return Text(
                          _controller.game.error!,
                          style: const TextStyle(color: Colors.red),
                        );
                      } else if (_controller.game.success != null) {
                        return Text(
                          _controller.game.success!,
                          style: const TextStyle(color: Colors.green),
                        );
                      }
                      return const SizedBox
                          .shrink(); // Display nothing if no messages
                    },
                  ),
                ],
              ),
            ),
          );
        });
  }
}
