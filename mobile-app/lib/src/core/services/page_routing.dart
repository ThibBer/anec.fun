import 'package:anecdotfun/src/core/utils/constants.dart';
import 'package:anecdotfun/src/features/score/score_page.dart';
import 'package:anecdotfun/src/features/stick_passing/stick_passing_page.dart';
import 'package:anecdotfun/src/features/telling_annectode/telling_anecdote_page.dart';
import 'package:anecdotfun/src/features/voting/vote_page.dart';
import 'package:flutter/material.dart';

class GlobalNavigationService {
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();

  // Current game state listener setup
  static void listenToGameState(ValueNotifier<GameState> gameStateNotifier) {
    gameStateNotifier.addListener(() {
      final gameState = gameStateNotifier.value;
      switch (gameState) {
        case GameState.connected:
          navigateTo(VotePage.routeName);
          break;
        case GameState.roundStarted:
          navigateTo(StickPassingPage.routeName);
          break;
        case GameState.scores:
          navigateTo(PlayerScorePage.routeName);
          break;
        case GameState.voting:
          navigateTo(VotePage.routeName);
          break;
        case GameState.stickExploded:
          navigateTo(TellingAnecdotePage.routeName);
          break;
        default:
          // Handle other states if needed
          break;
      }
    });
  }

  // Navigation logic
  static void navigateTo(String routeName, {Object? arguments}) {
    navigatorKey.currentState
        ?.pushReplacementNamed(routeName, arguments: arguments);
  }

  static void pop([Object? result]) {
    navigatorKey.currentState?.pop(result);
  }
}
