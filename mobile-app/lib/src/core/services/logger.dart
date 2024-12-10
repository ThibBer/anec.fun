import 'package:logger/logger.dart';

class AppLogger {
  // Singleton instance
  static final Logger _logger = Logger();

  // Getter to access the logger
  static Logger get instance => _logger;
}
