import 'package:flutter/material.dart';
import 'package:nfc_manager/nfc_manager.dart';

import '../../core/models/game.dart';
import '../../core/services/web_socket_connection.dart';

class StickPassingController extends ChangeNotifier {
  final Game game;
  final WebSocketConnection webSocketConnection;

  bool isScanning = false;
  bool isSuccess = false;
  bool isExploded = false;
  BuildContext context;

  StickPassingController({
    required this.webSocketConnection,
    required this.game,
      required this.context}) {
    _initialize();
  }

  Future<void> _initialize() async {
    if (await isNfcAvailable()) {
      isScanning = true;
    }
  }

  Future<bool> isNfcAvailable() async {
    return await NfcManager.instance.isAvailable();
  }

  /// Starts the NFC scan and handles session logic
  Future<void> startNfcScan() async {
    isScanning = true;
    isSuccess = false;
    notifyListeners();

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
    final payload = tag.data['payload'];
    if (payload != null &&
        String.fromCharCodes(payload.sublist(1 + payload[0])) == "stick") {
      if (isStickExploded()) {
        isScanning = false;
        isExploded = true;
        notifyListeners();

        Future.delayed(const Duration(seconds: 2), onScanSuccessful);
      } else {
        isScanning = false;
        isSuccess = true;
        notifyListeners();
      }
    }

    NfcManager.instance.stopSession();
  }

  /// Validates the scan after timeout
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

    Future.delayed(const Duration(seconds: 2), () {
      onScanSuccessful();
    });
  }

  bool isStickExploded() {
    return game.stickExploded;
  }

  /// Handles successful scan logic
  void onScanSuccessful() async {
    webSocketConnection.sendStickScanned();
    isExploded = false;
    isSuccess = false;
    if (await isNfcAvailable()) {
      isScanning = true;
    }
    notifyListeners();
  }

  /// Displays a toast message
  void showToast(String message) {
    // Placeholder for showing a toast
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  void dispose() {
    super.dispose();
  }
}
