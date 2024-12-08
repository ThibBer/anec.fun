import 'package:anecdotfun/src/core/utils/constants.dart';
import 'package:anecdotfun/src/features/connection/connection_page.dart';
import 'package:anecdotfun/src/features/leaderboard/leaderboard_page.dart';
import 'package:anecdotfun/src/features/score/score_page.dart';
import 'package:anecdotfun/src/features/stick_passing/stick_passing_page.dart';
import 'package:anecdotfun/src/features/telling_annectode/telling_anecdote_page.dart';
import 'package:anecdotfun/src/features/voting/vote_page.dart';
import 'package:flutter/material.dart';

class GlobalNavigationService {
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();
  static String currentRoute = ConnectionPage.routeName;

  // Current game state listener setup
  static void listenToGameState(ValueNotifier<GameState> gameStateNotifier) {
    gameStateNotifier.addListener(() {
      final gameState = gameStateNotifier.value;
      switch (gameState) {
        case GameState.connected:
          print("navigate to vote page");
          navigateTo(VotePage.routeName);
          break;
        case GameState.disconnected:
          print("navigate to connection page");
          navigateTo(ConnectionPage.routeName);
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
        case GameState.stickPassingDone:
          navigateTo(TellingAnecdotePage.routeName);
          break;
        case GameState.stopped:
          navigateTo(LeaderboardPage.routeName);
          break;
        default:
          // Handle other states if needed
          break;
      }
    });
  }

  // Navigation logic
  static void navigateTo(String routeName, {Object? arguments}) {
    final currentState = navigatorKey.currentState;

    if (currentState == null) return;

    // Check if the routeName is already the current route
    if (currentRoute == routeName) {
      print("Already on $routeName, skipping navigation.");
      return;
    }

    // Update the current route and perform navigation
    currentRoute = routeName;
    currentState.pushReplacementNamed(routeName, arguments: arguments);
  }

  static void pop([Object? result]) {
    navigatorKey.currentState?.pop(result);

    // Reset the current route after popping
    final context = navigatorKey.currentContext;
    if (context != null) {
      currentRoute = ModalRoute.of(context)!.settings.name!;
    }
  }
}
