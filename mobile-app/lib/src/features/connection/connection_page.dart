import 'package:flutter/material.dart';
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
    _controller = ConnectionController();
  }

  @override
  void dispose() {
    _controller.disposeController();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Connect to Box'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              Navigator.restorablePushNamed(context, SettingsView.routeName);
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
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
              animation: _controller,
              builder: (context, child) {
                return _controller.isConnecting
                    ? const CircularProgressIndicator()
                    : ElevatedButton(
                        onPressed: () {
                          _controller.submitForm(
                            onSuccess: () {
                              Navigator.pushNamed(context, VotePage.routeName);
                            },
                            onError: (error) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text(error)),
                              );
                            },
                          );
                        },
                        child: const Text('Connect'),
                      );
              },
            ),
            const SizedBox(height: 20),
            AnimatedBuilder(
              animation: _controller,
              builder: (context, child) {
                if (_controller.connectionMessage != null) {
                  return Text(
                    _controller.connectionMessage!,
                    style: TextStyle(
                      color:
                          _controller.connectionMessage!.startsWith('Error') ||
                                  _controller.connectionMessage!
                                      .startsWith('Failed')
                              ? Colors.red
                              : Colors.green,
                    ),
                  );
                }
                return const SizedBox.shrink();
              },
            ),
          ],
        ),
      ),
    );
  }
}
