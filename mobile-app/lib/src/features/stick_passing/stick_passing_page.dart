import 'package:anecdotfun/src/features/telling_annectode/telling_anecdote_page.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart'; // For animations
import 'stick_passing_controller.dart';
import '../../core/utils/constants.dart';
import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';
import 'package:nfc_manager/nfc_manager.dart';

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

  bool isStickNfc(List<int> payload) {
    if (String.fromCharCodes(payload.sublist(1 + payload[0])) == "stick") {
      return true;
    }
    return false;
  }

  Future<void> _startNfcScan() async {
    setState(() {
      _isScanning = true;
      _isSuccess = false;
    });

    // Check availability
    bool isAvailable = await NfcManager.instance.isAvailable();

    // Start a timer to handle the timeout
    Future.delayed(Duration(seconds: 5), () {
      if (_isScanning) {
        // If still scanning after 5 seconds, auto-validate
        _validateScan();
      }
    });

    if (!isAvailable) {
      // Send a message to the user that NFC is not available
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("NFC is not available")),
      );
    } else {
      // Start NFC session
      NfcManager.instance.startSession(
        pollingOptions: {NfcPollingOption.iso14443},
        onDiscovered: (NfcTag tag) async {
          print(tag.data);

          // Stop the timeout validation
          if (_isScanning) {
            _validateTag(tag);
          }
        },
      );
    }
  }

  void _validateTag(NfcTag tag) {
    if (isStickNfc(tag.data['payload'])) {
      if (_controller.validateStick()) {
        setState(() {
          _isScanning = false;
          _isExploded = true;
        });
        // sleep for 2 seconds to show the explosion animation
        Future.delayed(Duration(seconds: 2), () {
          _controller.onScanSuccessful();
        });
      } else {
        setState(() {
          _isScanning = false;
          _isSuccess = true;
        });
      }
    }

    // Stop the session
    NfcManager.instance.stopSession();
  }

  void _validateScan() {
    if (_controller.validateStick()) {
      setState(() {
        _isScanning = false;
        _isExploded = true;
        _isSuccess = false;
      });
    } else {
      setState(() {
        _isScanning = false;
        _isSuccess = true;
        _isExploded = false;
      });
    }
    // sleep for 2 seconds to show the animation
    Future.delayed(Duration(seconds: 2), () {
      // Handle any logic for timeout
      if (_controller.validateStick()) {
        _controller
            .onScanSuccessful(); // Optional, add a callback for timeout handling
      }
      NfcManager.instance.stopSession();
      setState(() {
        _isScanning = false;
        _isExploded = false;
        _isSuccess = false; // Default state if timeout happens
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
        listenable: _controller.game,
        builder: (context, _) {
          if (_controller.game.state == GameState.stickExploded) {
            // Wait for explosion animation to finish
            while (_isExploded) {
              // sleep 0.1 seconds
              Future.delayed(Duration(milliseconds: 100));
            }
            WidgetsBinding.instance.addPostFrameCallback((_) {
              Navigator.pushNamed(context, TellingAnecdotePage.routeName);
            });
          }
          // if (_controller.game.state == GameState.voting) {
          //   WidgetsBinding.instance.addPostFrameCallback((_) {
          //     Navigator.pushNamed(context, VotePage.routeName);
          //   });
          // }
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
                      renderCache: RenderCache.raster,
                    ),
                    const Text('Scanning for the stick...'),
                  ] else if (_isSuccess) ...[
                    Lottie.asset(
                      'assets/animations/success.json', // Replace with a proper success animation
                      renderCache: RenderCache.raster,
                    ),
                    const Text('Stick successfully passed!'),
                  ] else if (_isExploded) ...[
                    Expanded(
                      child: Lottie.asset(
                      'assets/animations/explosion.json', // Replace with a proper explosion animation
                        renderCache: RenderCache.raster,
                        fit: BoxFit.contain,
                      ),
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
