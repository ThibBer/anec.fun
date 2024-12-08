class Player {
  String username;
  String? vote;
  int score;

  Player({
    required this.username,
    this.vote,
    this.score = 0,
  });

  /// Updates the vote for the player
  void updateVote(String newVote) {
    vote = newVote;
  }

  /// Increments the player's score
  void incrementScore() {
    score++;
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
  factory Player.fromMap(Map<String, dynamic> map) {
    return Player(
      username: map['username'],
      vote: map['vote'],
      score: map['score'],
    );
  }
}
