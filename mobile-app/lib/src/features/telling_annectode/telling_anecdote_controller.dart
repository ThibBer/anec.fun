import 'package:flutter/foundation.dart';
import 'package:anecdotfun/src/core/models/game.dart';

class TellingAnecdoteController {
  final ValueNotifier<bool> isRecording = ValueNotifier<bool>(false);
  final String theme = "Funny";

  final Game game;

  TellingAnecdoteController({required this.game});

  bool get isTellingOwnAnecdote => game.uniqueId == game.annecdotTellerId;

  String get currentTellerUsername =>
      game.players[game.annecdotTellerId]?["username"] ?? "Unknown";

  void dispose() {
    isRecording.dispose();
  }
}
