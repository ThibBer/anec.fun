import 'package:flutter/material.dart';
import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';
import 'score_controller.dart';
import 'package:anecdotfun/src/features/stick_passing/stick_passing_page.dart';
import '../../core/utils/constants.dart';

class PlayerScorePage extends StatefulWidget {
  static const routeName = '/player-scores';

  const PlayerScorePage({super.key});

  @override
  PlayerScorePageState createState() => PlayerScorePageState();
}

class PlayerScorePageState extends State<PlayerScorePage> {
  late final PlayerScoreController scoreController;

  @override
  void initState() {
    super.initState();
    scoreController = PlayerScoreController(
      webSocketConnection: WebSocketConnection(),
      game: Game(),
    );
  }

  @override
  void dispose() {
    //scoreController.webSocketConnection.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: scoreController.game,
      builder: (context, _) {
        if (scoreController.game.state == GameState.roundStarted) {
          // Schedule navigation after the build completes
          WidgetsBinding.instance.addPostFrameCallback((_) {
            Navigator.pushReplacementNamed(context, StickPassingPage.routeName);
          });
        }
        return Scaffold(
          appBar: AppBar(
            title: Text(
              "Box ${scoreController.game.boxId} - Scores",
              textAlign: TextAlign.center,
            ),
            centerTitle: true,
          ),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                // Header card for scores
                Card(
                  elevation: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      "Current Player Scores",
                      style: Theme.of(context).textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
                // List of players and their scores
                Expanded(
                  child: ListView.builder(
                    itemCount: scoreController.game.players.length,
                    itemBuilder: (context, index) {
                      String uniqueId =
                          scoreController.game.players.keys.elementAt(index);
                      int score =
                          scoreController.game.playerScores[uniqueId] ?? 0;
                      return Card(
                        margin: const EdgeInsets.symmetric(vertical: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.blue,
                            child: Text(
                              (index + 1).toString(),
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                          title: Text('Player: $uniqueId'),
                          subtitle: Text('Score: $score pts'),
                        ),
                      );
                    },
                  ),
                ),
                // Footer or actions if required
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16.0),
                  child: Text(
                    'Push on the box to start next round.',
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
