import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart'; // For animations
import 'stick_passing_controller.dart';
import '../../core/utils/constants.dart';
import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';
import '../voting/vote_page.dart';

class StickPassingPage extends StatefulWidget {
  static const routeName = '/stick_passing';

  const StickPassingPage({super.key});

  @override
  State<StickPassingPage> createState() => _StickPassingPageState();
}

class _StickPassingPageState extends State<StickPassingPage> {
  late final StickPassingController _controller;
  bool _isSuccess = false;
  bool _isExploded = false;
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    _controller = StickPassingController(
      webSocketConnection: WebSocketConnection(),
      game: Game(),
    );
  }

  @override
  void dispose() {
    _controller.disposeController();
    super.dispose();
  }

  Future<void> _startNfcScan() async {
    setState(() {
      _isScanning = true;
      _isSuccess = false;
    });

    await Future.delayed(const Duration(seconds: 2));

    setState(() async {
      _isScanning = false;
      if (_controller.validateStick()) {
        _isExploded = true;
        _controller.sendVotingState();
      } else {
        _isSuccess = true;
        await Future.delayed(const Duration(seconds: 2));
        _startNfcScan();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
        listenable: _controller.game,
        builder: (context, _) {
          if (_controller.game.state == GameState.voting) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              Navigator.pushNamed(context, VotePage.routeName);
            });
          }
          return Scaffold(
            appBar: AppBar(
              title: const Text(
                  'Tap the Stick against the phone and pass it to the next player'),
            ),
            body: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (_isScanning) ...[
                    Lottie.asset(
                      'assets/animations/scanner.json', // Replace with a proper scanner animation
                      width: 150,
                      height: 150,
                    ),
                    const Text('Scanning for the stick...'),
                  ] else if (_isSuccess) ...[
                    Lottie.asset(
                      'assets/animations/success.json', // Replace with a proper success animation
                      width: 150,
                      height: 150,
                    ),
                    const Text('Stick successfully passed!'),
                  ] else if (_isExploded) ...[
                    Lottie.asset(
                      'assets/animations/explosion.json', // Replace with a proper explosion animation
                      width: 150,
                      height: 150,
                    ),
                    const Text('Stick exploded! Game over!'),
                  ] else ...[
                    ElevatedButton(
                      onPressed: _startNfcScan,
                      child: const Text('Start NFC Scan'),
                    ),
                  ],
                ],
              ),
            ),
          );
        });
  }
}
