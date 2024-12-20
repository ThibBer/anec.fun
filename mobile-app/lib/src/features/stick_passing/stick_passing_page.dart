import 'package:anecdotfun/src/core/services/page_routing.dart';
import 'package:anecdotfun/src/settings/settings_view.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:nfc_manager/nfc_manager.dart';

import '../../core/models/game.dart';
import '../../core/services/web_socket_connection.dart';
import 'stick_passing_controller.dart';

class StickPassingPage extends StatefulWidget {
  static const routeName = '/stick_passing';

  const StickPassingPage({super.key});

  @override
  State<StickPassingPage> createState() => _StickPassingPageState();
}

class _StickPassingPageState extends State<StickPassingPage>
    with TickerProviderStateMixin {
  late final StickPassingController _controller;

  bool _isNfcAvailable = true; // Default to true until checked
  bool _isNfcChecked = false; // To ensure the check runs only once

  @override
  void initState() {
    super.initState();
    _controller = StickPassingController(
      webSocketConnection: WebSocketConnection(),
      game: Game(),
      context: context,
      vsync: this,
    );
    // Listen to controller changes
    _controller.addListener(() {
      setState(() {});
    });

    // Check NFC availability
    _checkNfcAvailability();

    GlobalNavigationService.listenToGameState(_controller.game.state);
  }

  Future<void> _checkNfcAvailability() async {
    _isNfcAvailable = await _controller.isNfcAvailable();
    _isNfcChecked = true;
    setState(() {});
  }

  @override
  void dispose() {
    _controller.isNfcAvailable().then((isAvailable) {
      if (isAvailable) {
        NfcManager.instance.stopSession();
      }
    });

    _controller.dispose();

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: ListenableBuilder(
        listenable: _controller.game,
        builder: (context, _) {
          return Scaffold(
            appBar: AppBar(
              title: const Text('Stick Passing'),
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
            body: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (_controller.isScanning) ...[
                    Lottie.asset(
                      'assets/animations/scanner.json',
                    ),
                    const Text('Scanning for the stick...'),
                  ] else if (_controller.isSuccess) ...[
                    Lottie.asset(
                      'assets/animations/success.json',
                        controller: _controller.animationController,
                        onLoaded: (composition) {
                      _controller.successComposition = composition;
                      _controller.animationController
                        ..duration = composition.duration
                        ..forward();
                    }
                    ),
                    const Text('Stick successfully passed!'),
                  ] else if (_controller.isExploded) ...[
                    Expanded(
                      child: Lottie.asset(
                        'assets/animations/explosion.json',
                        fit: BoxFit.contain,
                      ),
                    ),
                    const Text(
                        'Stick exploded! Your turn to tell an anecdote!'),
                  ] else
                    ..._displayManualScan(),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  List<Widget> _displayManualScan() {
    if (!_isNfcChecked) {
      return [const CircularProgressIndicator(), const Text('Checking NFC...')];
    }

    if (!_isNfcAvailable) {
      return [
        const Text('NFC is not available on this device'),
        ElevatedButton(
          onPressed: _controller.validateScanByTap,
          child: const Text('Tap to validate the stick'),
        ),
      ];
    }

    return [const Text('NFC is available. Ready to scan!')];
  }
}
