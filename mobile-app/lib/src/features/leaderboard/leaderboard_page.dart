import 'package:anecdotfun/src/core/utils/disconnection_overlay.dart';
import 'package:anecdotfun/src/settings/settings_view.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart'; // Add this dependency to your pubspec.yaml
import '../../core/models/game.dart';
import 'leaderboard_controller.dart';

class LeaderboardPage extends StatefulWidget {
  static const routeName = '/leaderboard';

  const LeaderboardPage({super.key});

  @override
  LeaderboardPageState createState() => LeaderboardPageState();
}

class LeaderboardPageState extends State<LeaderboardPage>
    with SingleTickerProviderStateMixin {
  late final LeaderboardController leaderboardController;

  @override
  void initState() {
    super.initState();
    leaderboardController = LeaderboardController(game: Game());
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Stack(
        fit: StackFit.expand,
        alignment: Alignment.center,
        children: [
          Scaffold(
          appBar: AppBar(
            title: const Text(
              'Leaderboard',
              textAlign: TextAlign.center,
            ),
              automaticallyImplyLeading: false,
            centerTitle: true,
              actions: [
                IconButton(
                  icon: const Icon(Icons.settings),
                  onPressed: () {
                    Navigator.restorablePushNamed(
                        context, SettingsView.routeName);
                  },
                ),
              ],
          ),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                Card(
                  elevation: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                      child: Builder(
                        builder: (context) {
                          final rank = leaderboardController.getPlayerRank(
                              leaderboardController.game.uniqueId);
                          String message;
                          if (rank == 1) {
                            message =
                                "Congratulations ${leaderboardController.game.username}! You're the winner!";
                          } else if (rank == 2) {
                            message =
                                "Well done ${leaderboardController.game.username}! You finished in second place!";
                          } else if (rank == 3) {
                            message =
                                "Good job ${leaderboardController.game.username}! You secured third place!";
                          } else if (rank > 3) {
                            message =
                                "Keep trying ${leaderboardController.game.username}! You finished in position $rank.";
                          } else {
                            message =
                                "Oops! Something went wrong. Your rank couldn't be determined.";
                          }
                          return Text(
                            message,
                            style: Theme.of(context).textTheme.headlineSmall,
                            textAlign: TextAlign.center,
                          );
                        },
                    ),
                  ),
                  ),
                Expanded(
                  child: ListView.builder(
                    itemCount: leaderboardController.sortedPlayers.length,
                      itemBuilder: (context, index) {
                      final player = leaderboardController.sortedPlayers[index];
                      return Card(
                        margin: const EdgeInsets.symmetric(vertical: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.green,
                            child: Text(
                              (index + 1).toString(),
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                          title: Text('Player: ${player.username}'),
                          subtitle: Text('Score: ${player.score} pts'),
                          trailing: Icon(
                              index == 0
                                  ? Icons.emoji_events
                                  : Icons.star_border,
                            color: index == 0 ? Colors.yellow : Colors.grey,
                          ),
                        ),
                      );
                    },
                  ),
                ),
                  Padding(
                  padding: EdgeInsets.symmetric(vertical: 16.0),
                    child: ElevatedButton(
                      onPressed: () {
                        leaderboardController.disconnect();
                      },
                      child: Text('Go to connection page'),
                  ),
                ),
              ],
            ),
          ),
          ),
          if (leaderboardController.game.isWinner())
            IgnorePointer(
              ignoring:
                  true, // Allow interactions with widgets under this overlay
              child: Lottie.asset(
                'assets/animations/confetti.json',
                fit: BoxFit.fill,
              ),
            ),
          disconnectionOverlay(leaderboardController),
        ],

      ),
    );
  }
}
