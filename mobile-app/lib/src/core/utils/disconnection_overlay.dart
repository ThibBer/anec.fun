import 'package:anecdotfun/src/core/utils/constants.dart';
import 'package:flutter/material.dart';

Widget disconnectionOverlay(controller) => ValueListenableBuilder<GameState>(
      valueListenable: controller.game.state,
      builder: (context, gameState, child) {
        if (gameState == GameState.disconnecting) {
          return AbsorbPointer(
            absorbing: true, // Block interactions
            child: Container(
              width: double.infinity,
              height: double.infinity,
              color: Colors.black.withOpacity(0.5), // Optional overlay
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(color: Colors.white),
                  const SizedBox(height: 16),
                  DefaultTextStyle(
                    style: Theme.of(context).textTheme.bodyLarge!,
                    child: const Text(
                      'Disconnecting...',
                      style: TextStyle(color: Colors.white, fontSize: 16),
                    ),
                  ),
                ],
              ),
            ),
          );
        }
        return const SizedBox.shrink();
      },
    );
