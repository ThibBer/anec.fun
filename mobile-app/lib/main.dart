import 'package:anecdotfun/src/test/config.dart';
import 'package:anecdotfun/src/test/mock_game_setup.dart';
import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'src/app.dart';
import 'src/settings/settings_controller.dart';
import 'src/settings/settings_service.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  WakelockPlus.enable(); // Keep the screen on
  usePathUrlStrategy();

  final settingsController = SettingsController(SettingsService());
  await settingsController.loadSettings();

  // Conditionally set up mock data based on the flag
  if (AppConfig.useMockData) {
    setupMockGame();
  }

  runApp(MyApp(
    settingsController: settingsController,
    initialRoute: AppConfig.initialRoute,
  ));
}
