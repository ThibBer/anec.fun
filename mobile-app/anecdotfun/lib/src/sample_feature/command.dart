abstract class Command {
  final int boxId;

  Command(this.boxId);

  factory Command.fromJson(Map<String, dynamic> json) {
    String? commandType = json['commandType'];
    int boxId = json['box_id'];

    switch (commandType) {
      case 'StartGameCommand':
        return StartGameCommand(boxId);
      case 'StopGameCommand':
        return StopGameCommand(boxId);
      case 'VoteCommand':
        return VoteCommand(boxId, json['remote_id'], json['vote']);
      case 'ConnectRemote':
        return ConnectRemote(boxId, json['remote_id']);
      case 'DisconnectRemote':
        return DisconnectRemote(boxId, json['remote_id']);
      // Add cases for other commands and notifications
      default:
        throw UnsupportedError('Unknown command type');
    }
  }
}

class StartGameCommand extends Command {
  StartGameCommand(int boxId) : super(boxId);
}

class StopGameCommand extends Command {
  StopGameCommand(int boxId) : super(boxId);
}

class VoteCommand extends Command {
  final int remoteId;
  final bool vote;

  VoteCommand(int boxId, this.remoteId, this.vote) : super(boxId);
}

class ConnectRemote extends Command {
  final int remoteId;

  ConnectRemote(int boxId, this.remoteId) : super(boxId);
}

class DisconnectRemote extends Command {
  final int remoteId;

  DisconnectRemote(int boxId, this.remoteId) : super(boxId);
}
