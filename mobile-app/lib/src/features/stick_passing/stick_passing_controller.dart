import 'package:anecdotfun/src/core/services/logger.dart';
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
      isSuccess = false;
      isScanning = false;
      isExploded = true;
      webSocketConnection.sendExplodedAnimationPlayed();
      notifyListeners();
      Future.delayed(const Duration(seconds: 2), () {
        isExploded = false;
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
              isScanning = false;
              isSuccess = true;
              notifyListeners();
              webSocketConnection.sendStickScanned();
              playSuccessAnimation();
            } else {
              AppLogger.instance.i("Invalid tag");
            }
          } else {
            AppLogger.instance.i("Invalid tag");
          }
        } else {
          AppLogger.instance.i("Invalid tag");
        }
      } else {
        AppLogger.instance.i("Invalid tag");
      }
    } else {
      AppLogger.instance.i("Invalid tag");
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
    isSuccess = false;
    bool nfcAvailable = await isNfcAvailable();
    if (isStickExploded()) {
      isExploded = false;
      isScanning = false;
      isSuccess = false;
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
