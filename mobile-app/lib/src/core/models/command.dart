/// An abstract base class for commands sent over the WebSocket.
///
/// This class has a concrete factory method that takes a JSON object and a box ID
/// and returns an instance of a concrete command class based on the command type
/// in the JSON object.
abstract class Command {
  final int boxId;

  Command(this.boxId);

  factory Command.fromJson(Map<String, dynamic> json, int boxId) {
    String? commandType = json['commandType'];
    String? uniqueId = json['uniqueId'];
    switch (commandType) {
      case 'Connection':
        return Connection(boxId, uniqueId!);
      case 'StartGameCommand':
        return StartGameCommand(boxId);
      case 'StopGameCommand':
        return StopGameCommand(boxId);
      case 'VoteCommand':
        return VoteCommand(boxId, uniqueId!, json['message']);
      case 'ConnectRemote':
        return ConnectRemote(boxId, uniqueId!);
      case 'DisconnectRemote':
        return DisconnectRemote(boxId, uniqueId!);
      case 'StatusCommand':
        return StatusCommand(boxId, json['status'], json['message']);
      case 'StickExploded':
        return StickExploded(boxId);
      case 'VoteResult':
        return VoteResult(boxId);
      case 'AnnecdotTeller':
        return AnnecdotTeller(boxId);
      case 'RetrieveStateCommand':
        return RetrieveStateCommand(boxId);
      case 'GameModeChanged':
        return GameModeChanged(boxId, json["message"]);
      case 'ClientDisconnected':
        return ClientDisconnected(boxId, json["senderUniqueId"]);
      case 'SubjectChanged':
        return SubjectChanged(boxId, json["message"]);
      case 'StickScanned':
        return StickScanned(boxId, uniqueId!);
      case 'PlayStickExploded':
        return PlayStickExploded(boxId);
      default:
        throw UnsupportedError('Unknown command type: $commandType');
    }
  }
}

class Connection extends Command {
  final String uniqueId;
  Connection(super.boxId, this.uniqueId);
}

class StartGameCommand extends Command {
  StartGameCommand(super.boxId);
}

class StartRoundCommand extends Command {
  StartRoundCommand(super.boxId);
}

class StopGameCommand extends Command {
  StopGameCommand(super.boxId);
}

class VoteCommand extends Command {
  final String uniqueId;
  final String vote;

  VoteCommand(super.boxId, this.uniqueId, this.vote);
}

class ConnectRemote extends Command {
  final String uniqueId;

  ConnectRemote(super.boxId, this.uniqueId);
}

class DisconnectRemote extends Command {
  final String uniqueId;

  DisconnectRemote(super.boxId, this.uniqueId);
}

class StatusCommand extends Command {
  final String status;
  final String message;
  StatusCommand(super.boxId, this.status, this.message);
}

class StickExploded extends Command {
  StickExploded(super.boxId);
}

class VoteResult extends Command {
  VoteResult(super.boxId);
}

class AnnecdotTeller extends Command {
  AnnecdotTeller(super.boxId);
}

class RetrieveStateCommand extends Command {
  RetrieveStateCommand(super.boxId);
}

class GameModeChanged extends Command {
  final String gameMode;

  GameModeChanged(super.boxId, this.gameMode);
}

class ClientDisconnected extends Command {
  final String disconnectedUserId;

  ClientDisconnected(super.boxId, this.disconnectedUserId);
}

class SubjectChanged extends Command {
  final String subject;

  SubjectChanged(super.boxId, this.subject);
}

class StickScanned extends Command {
  final String uniqueId;

  StickScanned(super.boxId, this.uniqueId);
}

class PlayStickExploded extends Command {
  PlayStickExploded(super.boxId);
}
