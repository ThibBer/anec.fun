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
  }

  @override
  void dispose() {
    voteController.webSocketConnection.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
        listenable: voteController.game,
        builder: (context, _) {
          return Scaffold(
            appBar: AppBar(
              title: Text(
                "Box ${voteController.game.boxId} - ${voteController.game.state.toString().split('.').last} phase",
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
                      child: Text(
                        "Anecdote theme: *put theme here*",
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
                  // List of players and their vote status
                  Expanded(
                    child: ListView.builder(
                      itemCount: voteController.game.players.length,
                      itemBuilder: (context, index) {
                        String uniqueId =
                            voteController.game.players.keys.elementAt(index);
                        Map<String, dynamic> playerData =
                            voteController.game.players[uniqueId]!;
                        String voteStatus = playerData["vote"] == null
                            ? 'No vote yet'
                            : playerData["vote"] == "true"
                                ? 'Voted: True'
                                : 'Voted: False';

                        return Card(
                          margin: const EdgeInsets.symmetric(vertical: 8),
                          child: ListTile(
                            leading: CircleAvatar(
                              backgroundColor: Colors.blue,
                              child: Text(
                                (index + 1).toString(), // Use index for avatar
                                style: const TextStyle(color: Colors.white),
                              ),
                            ),
                            title: Text('Player: $uniqueId'),
                            subtitle: Text(voteStatus),
                          ),
                        );
                      },
                    ),
                  ),
                  // Voting buttons
                  voteController.game.state == GameState.voting
                      ? Padding(
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
                                  textStyle:
                                      Theme.of(context).textTheme.bodyMedium,
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
                                  textStyle:
                                      Theme.of(context).textTheme.bodyMedium,
                                ),
                              ),
                            ],
                          ),
                        )
                      : const Text(
                          'Voting is not enabled',
                          style: TextStyle(color: Colors.grey),
                        ),
                ],
              ),
            ),
          );
        });
  }
}
