# anecdotfun

A new Flutter project.

## Getting Started

This project is a starting point for a Flutter application that follows the
[simple app state management
tutorial](https://flutter.dev/to/state-management-sample).

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## Assets

The `assets` directory houses images, fonts, and any other files you want to
include with your application.

The `assets/images` directory contains [resolution-aware
images](https://flutter.dev/to/resolution-aware-images).

## Localization

This project generates localized messages based on arb files found in
the `lib/src/localization` directory.

To support additional languages, please visit the tutorial on
[Internationalizing Flutter apps](https://flutter.dev/to/internationalization).

## TODO

- [x] Handle new rounds (the box send a message to the server and the server to clients) -> redirect from result page to game screen.
- [x] keep connection alive
- [ ] Handle connection lost
- [ ] on vote page add feedback to tell who is telling the anecdote -> server must tell to clients who is telling the anecdote
- [ ] Don't allow to users to have the same username

## Testing the App with Mock Data

This app allows you to easily test individual features, such as the `LeaderboardPage`, by enabling mock data and setting the initial route dynamically. Here's how you can use and configure these tests:

### **1. Enable Mock Data**

To enable mock data, open the `config.dart` file located in the `src` directory and set the `useMockData` flag to `true`:

```dart
class AppConfig {
  static const bool useMockData = true; // Enable mock data
}
```

### **2. Change the Initial Route**

The app dynamically sets the initial route based on the `useMockData` flag. When mock data is enabled, the app will start on the `LeaderboardPage`. You can modify the behavior by editing the `initialRoute` in `config.dart`:

```dart
class AppConfig {
  static const bool useMockData = true;
  static const String initialRoute = useMockData
      ? '/leaderboard' // Default route for mock mode
      : '/connection'; // Default route for real mode
}
```

### **3. Setup Mock Data**

Mock data for testing the `LeaderboardPage` is defined in `mock_game_setup.dart`. It preloads the `Game` singleton with test data such as players and scores:

```dart
void setupMockGame() {
  final mockGame = Game();
  mockGame.boxId = 123;

  // Add mock players and scores
  mockGame.addPlayer('player1', 'Alice');
  mockGame.addPlayer('player2', 'Bob');
  mockGame.addPlayer('player3', 'Charlie');

  mockGame.playerScores['player1'] = 10;
  mockGame.playerScores['player2'] = 15;
  mockGame.playerScores['player3'] = 5;

  mockGame.updateState(GameState.scores);
}
```

This mock setup ensures that the `LeaderboardPage` displays data immediately upon launch.

### **4. Run the App in Mock Mode**

Run the app as usual using:

```bash
flutter run
```

When `useMockData` is set to `true`, the app will launch with mock data and navigate directly to the `LeaderboardPage`.

### **5. Disable Mock Data**

To restore the app to its real-world flow, set `useMockData` to `false` in `config.dart`:

```dart
class AppConfig {
  static const bool useMockData = false; // Disable mock data
}
```

The app will now start with its default flow and use live game data.

### **6. Debug Menu (Optional)**

If you want to manually navigate to different pages during testing, you can add a debug menu. See the following example:

```dart
case '/debug-menu':
  return Scaffold(
    appBar: AppBar(title: const Text('Debug Menu')),
    body: ListView(
      children: [
        ListTile(
          title: const Text('Leaderboard Page'),
          onTap: () => Navigator.pushNamed(context, LeaderboardPage.routeName),
        ),
        // Add more test pages here
      ],
    ),
  );
```

### **7. Automated Testing**

If you'd like to test the `LeaderboardPage` programmatically, you can write Flutter widget tests. For example, see the following test setup in `leaderboard_page_test.dart`:

```dart
void main() {
  group('LeaderboardPage Tests', () {
    late Game mockGame;

    setUp(() {
      mockGame = Game();
      mockGame.boxId = 123;
      mockGame.addPlayer('player1', 'Alice');
      mockGame.addPlayer('player2', 'Bob');
      mockGame.addPlayer('player3', 'Charlie');
      mockGame.playerScores['player1'] = 10;
      mockGame.playerScores['player2'] = 15;
      mockGame.playerScores['player3'] = 5;
    });

    testWidgets('LeaderboardPage displays sorted player scores', (WidgetTester tester) async {
      await tester.pumpWidget(MaterialApp(
        home: LeaderboardPage(),
      ));

      expect(find.text('Player: player2'), findsOneWidget); // Top player
      expect(find.text('Score: 15 pts'), findsOneWidget);
    });
  });
}
```

Run the automated tests with:

```bash
flutter test
```
