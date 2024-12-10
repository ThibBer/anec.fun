import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:collection/collection.dart';
import '../../core/models/game.dart';
import '../../core/services/web_socket_connection.dart';

class StickPassingController extends ChangeNotifier {
  final Game game;
  final WebSocketConnection webSocketConnection;
  late final AnimationController animationController;
  LottieComposition? successComposition;
  bool isScanning = false;
  bool isSuccess = false;
  bool isExploded = false;
  BuildContext context;

  StickPassingController({
    required this.webSocketConnection,
    required this.game,
    required this.context,
    required TickerProvider vsync,
  }) {
    animationController = AnimationController(vsync: vsync);
    animationController.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        onScanSuccessful();
      }
    });
    _initialize();
  }

  Future<void> _initialize() async {
    game.playStickExploded.addListener(_onPlayStickExploded);
    if (await isNfcAvailable()) {
      startNfcScan();
    }
  }

  void playSuccessAnimation() {
    animationController
      ..reset()
      ..forward();
  }

  @override
  void dispose() {
    animationController.dispose();
    super.dispose();
  }

  void _onPlayStickExploded() {
    if (game.playStickExploded.value) {
      print("_onPlayStickExploded, playing stick exploded animation");
      isSuccess = false;
      isScanning = false;
      isExploded = true;
      notifyListeners();
      Future.delayed(const Duration(seconds: 2), () {
        isExploded = false;
        webSocketConnection.sendExplodedAnimationPlayed();
        notifyListeners();
      });
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
              isSuccess = true;
              notifyListeners();
              webSocketConnection.sendStickScanned();
              playSuccessAnimation();
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
    isExploded = false;
    isSuccess = true;
    webSocketConnection.sendStickScanned();
    isScanning = false;
    notifyListeners();
    playSuccessAnimation();
  }

  bool isStickExploded() {
    return game.stickExploded;
  }

  /// Handles successful scan logic
  void onScanSuccessful() async {
    print("Scan successful");
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
  }

  /// Displays a toast message
  void showToast(String message) {
    // Placeholder for showing a toast
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}
