import 'package:anecdotfun/src/core/utils/constants.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:collection/collection.dart';
import '../../core/models/game.dart';
import '../../core/services/web_socket_connection.dart';

class StickPassingController extends ChangeNotifier {
  final Game game;
  final WebSocketConnection webSocketConnection;

  bool isScanning = false;
  bool isSuccess = false;
  bool isExploded = false;
  BuildContext context;

  StickPassingController(
      {required this.webSocketConnection,
      required this.game,
      required this.context}) {
    _initialize();
  }

  Future<void> _initialize() async {
    // Listen to game state changes
    game.state.addListener(_onGameStateChanged);

    if (await isNfcAvailable()) {
      startNfcScan();
    }
  }

  bool animationIsPlaying() {
    return isScanning || isSuccess || isExploded;
  }

  void _onGameStateChanged() {
    if (game.state.value == GameState.stickExploded) {
      if (!isExploded) {
        game.updateState(GameState.stickPassingDone);
      } else {
        _waitForAnimationToEnd().then((_) {
          game.updateState(GameState.stickPassingDone);
        });
      }
    }
  }

  Future<void> _waitForAnimationToEnd() async {
    if (animationIsPlaying()) {
      await Future.delayed(const Duration(seconds: 1));
      await _waitForAnimationToEnd(); // Recursively call until animations finish
    }
  }


  Future<bool> isNfcAvailable() async {
    return !kIsWeb && await NfcManager.instance.isAvailable();
  }

  /// Starts the NFC scan and handles session logic
  Future<void> startNfcScan() async {
    isScanning = true;
    isSuccess = false;
    notifyListeners();
    print("Starting NFC scan");
    NfcManager.instance.startSession(
      pollingOptions: {NfcPollingOption.iso14443},
      onDiscovered: (NfcTag tag) async {
        validateTag(tag);
        return;
      },
    );
  }

  /// Validates the NFC tag
  void validateTag(NfcTag tag) {
    print("Tag discovered: ${tag.data}");

    // Navigate the nested structure to get the payload
    final ndef = tag.data['ndef'];
    if (ndef != null) {
      final cachedMessage = ndef['cachedMessage'];
      if (cachedMessage != null) {
        final records = cachedMessage['records'];
        if (records != null && records.isNotEmpty) {
          final payload = records[0]['payload'];
          if (payload != null) {
            // Use ListEquality to compare lists
            const listEquality = ListEquality();
            if (listEquality
                .equals(payload, [2, 101, 110, 115, 116, 105, 99, 107])) {
              print("Stick scanned");
              isScanning = false;
              if (isStickExploded()) {
                isExploded = true;
              } else {
                isSuccess = true;
              }
              notifyListeners();
              Future.delayed(const Duration(seconds: 2), onScanSuccessful);
            } else {
              print("Invalid tag");
            }
          } else {
            print("Payload is null");
          }
        } else {
          print("No records found");
        }
      } else {
        print("No cachedMessage found");
      }
    } else {
      print("No NDEF data found");
    }
  }

  /// Validates the scan
  void validateScanByTap() {
    if (isStickExploded()) {
      isExploded = true;
      isSuccess = false;
    } else {
      isExploded = false;
      isSuccess = true;
    }

    isScanning = false;
    notifyListeners();

    onScanSuccessful();
  }

  bool isStickExploded() {
    return game.stickExploded;
  }

  /// Handles successful scan logic
  void onScanSuccessful() async {
    print("Scan successful");
    webSocketConnection.sendStickScanned();

    Future.delayed(const Duration(seconds: 2), () async {
      isSuccess = false;
      bool nfcAvailable = await isNfcAvailable();
      if (isStickExploded()) {
        isExploded = false;
        isScanning = false;
        isSuccess = false;
        print("Stick exploded");
      } else if (nfcAvailable) {
        isScanning = true;
        isExploded = false;
      } else {
        isExploded = false;
      }

      notifyListeners();
    });
  }

  /// Displays a toast message
  void showToast(String message) {
    // Placeholder for showing a toast
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

}
