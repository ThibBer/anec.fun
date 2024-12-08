class AppConfig {
  static const bool useMockData = false; // Set to `true` to enable mock mode
  static const String initialRoute = useMockData
      ? '/leaderboard' // Default route in mock mode
      : '/'; // Default route in real mode
}
