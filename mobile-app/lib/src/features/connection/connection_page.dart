import 'package:anecdotfun/src/core/models/game.dart';
import 'package:anecdotfun/src/core/services/page_routing.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../settings/settings_view.dart';
import 'connection_controller.dart';
import 'package:lottie/lottie.dart';

class ConnectionPage extends StatefulWidget {
  static const routeName = '/';

  const ConnectionPage({super.key});

  @override
  State<ConnectionPage> createState() => _ConnectionPageState();
}

class _ConnectionPageState extends State<ConnectionPage>
    with WidgetsBindingObserver {
  late final ConnectionController _controller;
  bool _isKeyboardVisible = false;
  @override
  void initState() {
    super.initState();
    _controller = ConnectionController(game: Game());
    _checkReconnection();
    GlobalNavigationService.listenToGameState(_controller.game.state);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    _controller.disposeController();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    final bottomInset = View.of(context).viewInsets.bottom;
    final newValue = bottomInset > 0.0;
    if (newValue != _isKeyboardVisible) {
      setState(() {
        _isKeyboardVisible = newValue;
      });
    }
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
    return PopScope(
      canPop: false,
      child: ListenableBuilder(
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
          return Scaffold(
            appBar: AppBar(
              title: const Text('Connect to Box'),
              automaticallyImplyLeading: false,
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
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Flexible(
                    flex: 8,
                    child: Visibility(
                      visible: !_controller.game.isConnecting,
                      replacement:
                          _buildLoadingWidget(), // Show loading widget while connecting
                      child:
                          _buildFormWidget(), // Show form when not connecting
                    ),
                  ),
                  const SizedBox(height: 20),
                  Flexible(
                    flex: 1,
                    child: AnimatedBuilder(
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
                        return const SizedBox.shrink();
                      },
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildLoadingWidget() {
    return Column(
      children: [
        Flexible(
          child: ColorFiltered(
            colorFilter: ColorFilter.mode(
              Theme.of(context).colorScheme.primary,
              BlendMode.modulate,
            ),
            child: Lottie.asset(
              'assets/animations/loading.json',
              repeat: true,
              renderCache: RenderCache.raster,
              fit: BoxFit.contain,
            ),
          ),
        ),
        const SizedBox(height: 20),
        const Text('Connecting... Please wait'),
      ],
    );
  }

  Widget _buildFormWidget() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Visibility(
          visible: !_isKeyboardVisible, // Show SVG only when keyboard is hidden
          child: Flexible(
            child: SvgPicture.asset(
              'assets/images/logo_long.svg',
              semanticsLabel: 'Dart Logo',
              fit: BoxFit.contain, // Ensure it scales properly
            ),
          ),
        ),
        Visibility(
          visible:
              !_isKeyboardVisible, // Show Lottie animation only when keyboard is hidden
          child: Flexible(
            child: Theme.of(context).brightness == Brightness.light
                ? Lottie.asset(
                    'assets/animations/welcome.json',
                    repeat: true,
                    renderCache: RenderCache.raster,
                    fit: BoxFit.contain, // Ensure it scales properly
                  )
                : Lottie.asset(
                    'assets/animations/welcome_white.json',
                    repeat: true,
                    renderCache: RenderCache.raster,
                    fit: BoxFit.contain, // Ensure it scales properly
                  ),
          ),
        ),
        Flexible(
          child: Form(
            key: _controller.formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                Flexible(
                  child: TextFormField(
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
                ),
                const SizedBox(height: 20),
                Flexible(
                  child: TextFormField(
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
                ),
                const SizedBox(height: 20),
                Flexible(
                  child: ElevatedButton(
                    onPressed: () {
                      _controller.submitForm();
                    },
                    child: const Text('Connect'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
