import 'package:flutter/foundation.dart';
import 'package:anecdotfun/src/core/models/game.dart';

class TellingAnecdoteController {
  final ValueNotifier<bool> isRecording = ValueNotifier<bool>(false);

  final Game game;

  TellingAnecdoteController({required this.game});

  /// Check if the current player is telling their own anecdote
  bool get isTellingOwnAnecdote => game.uniqueId == game.annecdotTellerId.value;

  /// Get the username of the current anecdote teller
  String get currentTellerUsername {
    final teller = game.players.value[game.annecdotTellerId.value];
    return teller?.username ?? "Unknown";
  }

  /// Dispose of resources
  void dispose() {
    isRecording.dispose();
  }
}
