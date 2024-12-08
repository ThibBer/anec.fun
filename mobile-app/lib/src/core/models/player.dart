import 'package:flutter/material.dart';

class Player extends ChangeNotifier {
  String uniqueId;
  String username;
  String? vote;
  int score;

  Player({
    required this.username,
    required this.uniqueId,
    this.vote,
    this.score = 0,
  });

  /// Updates the vote for the player
  void updateVote(String? newVote) {
    vote = newVote;
    notifyListeners();
  }

  void setScore(int newScore){
    print("Set score $newScore");
    score = newScore;
    notifyListeners();
  }

  /// Serializes the player into a Map for persistence
  Map<String, dynamic> toMap() {
    return {
      'username': username,
      'vote': vote,
      'score': score,
    };
  }

  /// Creates a Player instance from a Map
  factory Player.fromMap(String uniqueId, Map<String, dynamic> map) {
    return Player(
      uniqueId: uniqueId,
      username: map['username'],
      vote: map['vote'],
      score: map['score'],
    );
  }
}
