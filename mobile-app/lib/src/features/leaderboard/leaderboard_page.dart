import 'package:flutter/material.dart';
import '../../core/models/game.dart';
import 'leaderboard_controller.dart';

class LeaderboardPage extends StatefulWidget {
  static const routeName = '/leaderboard';

  const LeaderboardPage({super.key});

  @override
  LeaderboardPageState createState() => LeaderboardPageState();
}

class LeaderboardPageState extends State<LeaderboardPage> {
  late final LeaderboardController leaderboardController;

  @override
  void initState() {
    super.initState();
    // Initialize the leaderboard controller with a Game instance
    leaderboardController = LeaderboardController(game: Game());
  }

  @override
  void dispose() {
    // Dispose any resources if necessary
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: leaderboardController.game,
      builder: (context, _) {
        // Get the sorted players from the controller
        final sortedPlayers = leaderboardController.sortedPlayers;

        return Scaffold(
          appBar: AppBar(
            title: const Text(
              'Leaderboard',
              textAlign: TextAlign.center,
            ),
            centerTitle: true,
          ),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                // Header card for leaderboard
                Card(
                  elevation: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      "Game Leaderboard",
                      style: Theme.of(context).textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
                // List of players ranked by scores
                Expanded(
                  child: ListView.builder(
                    itemCount: sortedPlayers.length,
                    itemBuilder: (context, index) {
                      String uniqueId = sortedPlayers[index].key;
                      int score =
                          leaderboardController.getPlayerScore(uniqueId);
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
                          title: Text('Player: $uniqueId'),
                          subtitle: Text('Score: $score pts'),
                          trailing: Icon(
                            index == 0
                                ? Icons.emoji_events
                                : Icons.star_border, // Trophy for top player
                            color: index == 0 ? Colors.yellow : Colors.grey,
                          ),
                        ),
                      );
                    },
                  ),
                ),
                // Footer or additional information
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16.0),
                  child: Text(
                    'The top player wins the round!',
                    style: TextStyle(color: Colors.grey),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
