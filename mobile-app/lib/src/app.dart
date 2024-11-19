import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'features/connection/connection_page.dart';
import 'settings/settings_controller.dart';
import 'settings/settings_view.dart';
import 'features/voting/vote_page.dart';
import 'features/stick_passing/stick_passing_page.dart';
import 'features/score/score_page.dart';

class MyApp extends StatelessWidget {
  const MyApp({
    super.key,
    required this.settingsController,
  });

  final SettingsController settingsController;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: settingsController,
      builder: (BuildContext context, Widget? child) {
        return MaterialApp(
          restorationScopeId: 'app',
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [
            Locale('en', ''),
          ],
          onGenerateTitle: (BuildContext context) =>
              AppLocalizations.of(context)!.appTitle,
          theme: ThemeData(),
          darkTheme: ThemeData.dark(),
          themeMode: settingsController.themeMode,
          onGenerateRoute: (RouteSettings routeSettings) {
            return MaterialPageRoute<void>(
              settings: routeSettings,
              builder: (BuildContext context) {
                switch (routeSettings.name) {
                  case SettingsView.routeName:
                    return SettingsView(controller: settingsController);
                  case ConnectionPage.routeName:
                    return const ConnectionPage();
                  case VotePage.routeName:
                    return const VotePage();
                  case StickPassingPage.routeName:
                    return const StickPassingPage();
                  case PlayerScorePage.routeName:
                    return const PlayerScorePage();
                  default:
                    return const ConnectionPage();
                }
              },
            );
          },
        );
      },
    );
  }
}
