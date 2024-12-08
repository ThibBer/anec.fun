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
    if (await isNfcAvailable()) {
      startNfcScan();
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
    print("Scan successful");
    webSocketConnection.sendStickScanned();
    isSuccess = false;
    bool nfcAvailable = await isNfcAvailable();
    if (isStickExploded()) {
      print("Stick exploded");
    } else if (nfcAvailable) {
      isScanning = true;
      isExploded = false;
    } else {
      isExploded = false;
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

}
