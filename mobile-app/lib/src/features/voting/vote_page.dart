import 'package:anecdotfun/src/core/models/player.dart';
import 'package:anecdotfun/src/features/score/score_page.dart';
import 'package:anecdotfun/src/features/stick_passing/stick_passing_page.dart';
import 'package:flutter/material.dart';
import 'vote_controller.dart';
import '../../core/services/web_socket_connection.dart';
import '../../core/models/game.dart';
import '../../core/utils/constants.dart';

class VotePage extends StatefulWidget {
  static const routeName = '/vote';

  const VotePage({super.key});

  @override
  VotePageState createState() => VotePageState();
}

class VotePageState extends State<VotePage> {
  late final VoteController voteController;

  @override
  void initState() {
    super.initState();
    voteController = VoteController(
      webSocketConnection: WebSocketConnection(),
      game: Game(),
    );

    // Listen for game state changes
    voteController.game.state.addListener(() {
      final gameState = voteController.game.state.value;
      if (gameState == GameState.roundStarted) {
        Navigator.pushReplacementNamed(context, StickPassingPage.routeName);
      } else if (gameState == GameState.scores) {
        Navigator.pushReplacementNamed(context, PlayerScorePage.routeName);
      }
    });
  }

  @override
  void dispose() {
    //voteController.webSocketConnection.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: ValueListenableBuilder<GameState>(
          valueListenable: voteController.game.state,
          builder: (context, gameState, child) {
            return Text(
              "Box ${voteController.game.boxId} - ${voteController.game.state.toString().split('.').last} phase",
            );
          },
        ),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            // Display the anecdote theme
            Card(
              elevation: 4,
              margin: const EdgeInsets.only(bottom: 20),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: ValueListenableBuilder<GameMode>(
                    valueListenable: voteController.game.mode,
                    builder: (context, gameState, child) {
                      return Text(
                        "Mode : ${voteController.game.mode.value.name}",
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      );
                    }),
              ),
            ),
            // List of players and their vote status
            // List of players and their vote status
            Expanded(
              child: ValueListenableBuilder<Map<String, Player>>(
                valueListenable: voteController.game.players,
                builder: (context, players, child) {
                  return ListView.builder(
                    itemCount: players.length,
                    itemBuilder: (context, index) {
                      final player = players.values
                          .elementAt(index); // Access Player object
                      final voteStatus = player.vote == null
                          ? 'No vote yet'
                          : player.vote == "true"
                              ? 'Voted: True'
                              : 'Voted: False';

                      return Card(
                        margin: const EdgeInsets.symmetric(vertical: 8),
                        child: ListTile(
                          title: Text(player
                              .username), // Use Player's username property
                          subtitle: Text(voteStatus),
                        ),
                      );
                    },
                  );
                },
              ),
            ),

            ValueListenableBuilder<GameState>(
              valueListenable: voteController.game.state,
              builder: (context, gameState, child) {
                if (gameState == GameState.voting) {
                  return Text(
                    voteController.game.uniqueId ==
                            voteController.game.annecdotTellerId
                        ? "you are the speaker, is the anecdote true or false?"
                        : "you are the listener, vote true or false",
                    textAlign: TextAlign.center,
                  );
                } else {
                  return const SizedBox.shrink();
                }
              },
            ),
            ValueListenableBuilder<GameState>(
              valueListenable: voteController.game.state,
              builder: (context, gameState, child) {
                // Check if the current game state is 'voting'
                if (gameState == GameState.voting) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        ElevatedButton.icon(
                          onPressed: () {
                            voteController.submitVote(
                              voteController.game.uniqueId,
                              "true",
                            );
                          },
                          icon: const Icon(Icons.thumb_up),
                          label: const Text('True'),
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(
                              vertical: 12.0,
                              horizontal: 24.0,
                            ),
                            textStyle: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                        const SizedBox(width: 20),
                        ElevatedButton.icon(
                          onPressed: () {
                            voteController.submitVote(
                              voteController.game.uniqueId,
                              "false",
                            );
                          },
                          icon: const Icon(Icons.thumb_down),
                          label: const Text('False'),
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(
                              vertical: 12.0,
                              horizontal: 24.0,
                            ),
                            textStyle: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                  );
                } else {
                  // Display a placeholder message or empty space if voting is not enabled
                  return const Text(
                    'Voting is not enabled',
                    style: TextStyle(color: Colors.grey),
                  );
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
