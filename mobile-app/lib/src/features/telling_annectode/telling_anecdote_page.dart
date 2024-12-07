import 'package:anecdotfun/src/core/models/game.dart';
import 'package:anecdotfun/src/core/utils/constants.dart';
import 'package:anecdotfun/src/features/voting/vote_page.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'telling_anecdote_controller.dart';

class TellingAnecdotePage extends StatefulWidget {
  static const routeName = '/tellingAnecdote';

  const TellingAnecdotePage({super.key});

  @override
  TellingAnecdotePageState createState() => TellingAnecdotePageState();
}

class TellingAnecdotePageState extends State<TellingAnecdotePage> {
  late final TellingAnecdoteController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TellingAnecdoteController(game: Game());
  }

  @override
  void dispose() {
    _controller.dispose(); // Dispose the controller's resources
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: _controller.isRecording,
      builder: (context, isRecording, child) {
        if (_controller.game.state == GameState.voting) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            Navigator.pushReplacementNamed(context, VotePage.routeName);
          });
        }
        return Scaffold(
          appBar: AppBar(
            title: const Text("Telling Anecdote"),
            centerTitle: true,
          ),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                // Display anecdote theme
                _buildThemeCard(context),
                // Recording animation
                _buildRecordingAnimation(),
                // Recording status text
                _buildStatusText(context),
                const SizedBox(height: 20),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildThemeCard(BuildContext context) {
    return Card(
      elevation: 4,
      margin: const EdgeInsets.only(bottom: 20),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Text(
              "Mode : ${_controller.game.mode.name}",
              style: Theme.of(context).textTheme.headlineSmall,
              textAlign: TextAlign.center,
            ),
            Text(
              "Subject : ${_controller.game.subject}",
              style: Theme.of(context).textTheme.labelSmall,
              textAlign: TextAlign.center,
            )
          ],
        )
      ),
    );
  }

  Widget _buildRecordingAnimation() {
    return Expanded(
      child: Center(
        child: AspectRatio(
          aspectRatio: 1,
          child: Lottie.asset(
            'assets/animations/annecdot_telling.json',
            repeat: true,
            renderCache: RenderCache.raster,
            height: 200,
            width: 200,
          ),
        ),
      ),
    );
  }

  Widget _buildStatusText(BuildContext context) {
    final isTellingOwnAnecdote = _controller.isTellingOwnAnecdote;
    return Text(
      isTellingOwnAnecdote
          ? "Recording your anecdote..."
          : "${_controller.currentTellerUsername} is telling an anecdote...",
      style: Theme.of(context).textTheme.bodyLarge,
      textAlign: TextAlign.center,
    );
  }
}
