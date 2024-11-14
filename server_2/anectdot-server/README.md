# Anectdot Server

Welcome to the Anectdot Server project! This README will guide you through the setup and usage of the server.

## Table of Contents

- [Anectdot Server](#anectdot-server)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Installation](#installation)
  - [Usage](#usage)
  - [Available json commands with answers](#available-json-commands-with-answers)
    - [Box](#box)
    - [Remote](#remote)
  - [Testing](#testing)
  - [Game logic](#game-logic)
    - [Game States](#game-states)
    - [Commands and Game Flow](#commands-and-game-flow)
      - [Start the Game (`StartGameCommand`)](#start-the-game-startgamecommand)
      - [Stop the Game (`StopGameCommand`)](#stop-the-game-stopgamecommand)
      - [Connect the Box (`ConnectBox`)](#connect-the-box-connectbox)
      - [Connect a Remote (`ConnectRemote`)](#connect-a-remote-connectremote)
      - [Disconnect a Remote (`DisconnectRemote`)](#disconnect-a-remote-disconnectremote)
      - [Start Voting Phase (`StartVoting`)](#start-voting-phase-startvoting)
      - [Cast a Vote (`VoteCommand`)](#cast-a-vote-votecommand)
    - [Helper Methods](#helper-methods)
      - [`broadcastGameState`](#broadcastgamestate)
      - [`broadcastVote`](#broadcastvote)
    - [Typical Game Flow](#typical-game-flow)
      - [1. **Setup Phase**](#1-setup-phase)
      - [2. **Player Registration**](#2-player-registration)
      - [3. **Game Start**](#3-game-start)
      - [4. **Gameplay and Interactions**](#4-gameplay-and-interactions)
      - [5. **Voting Phase**](#5-voting-phase)
      - [6. **Game Stopping or Pausing**](#6-game-stopping-or-pausing)
      - [7. **Results and Cleanup**](#7-results-and-cleanup)
    - [Summary of Game Lifecycle Commands](#summary-of-game-lifecycle-commands)

## Introduction

Anectdot Server is a scala server that uses the akka library to handle websockets connections for the box and remote of the anectdot game. The server is responsible for managing the game state and handling the communication between the box and the remote.

## Installation

To install the Anectdot Server, follow these steps:

1. Clone the repository:

    ```sh
    git clone https://github.com/ThibBer/anec.fun
    ```

2. Navigate to the server directory:

    ```sh
    cd server_2/anectdot-server
    ```

3. Compile the project:

    ```sh
    sbt compile
    ```

4. Run the server:

    ```sh
    sbt run
    ```

## Usage

After starting the server you can open a websocket connection to `ws://localhost:8080/ws/{your-box-id}` to connect to the server.

```sh
websocat ws://localhost:8080/ws/{your-box-id}
```

Then you can send json messages to the server that respect the following schema:

- `ConnectRemote`: `{ "commandType": "ConnectRemote", "remote_id": 1 }`

```json
{"box_id": 2, "remote_id": 1, "commandType": "ConnectRemote"}
```

- `StartGameCommand`:

```json
{"box_id": 2, "commandType": "StartGameCommand"}
```

- `VoteCommand`: `{ "commandType": "VoteCommand", "vote": "yes" }`

```json
{"box_id": 2, "vote": "yes", "commandType": "VoteCommand"}
```

The game expect at least two remotes to be connected before allowing to start a game.
The game must be in voting state to allow voting commands from the remotes.

## Available json commands with answers

- **Initial connection**
  - answer: `{"commandType":"Connection","status":"success","uniqueId":"1"}`

### Box

- Connection:
  - command:`{ "box_id": 1, "uniqueId": "1","commandType": "ConnectBox"}`
  - possible answers:
    - `{ "uniqueId": "1", "commandType": "ConnectBox", "status": "success"}`
    - `{ "uniqueId": "1", "commandType": "ConnectBox", "status": "error", "message": "Box already connected" }`
- Starting a game *broadcasted to all remotes*:
  - command:`{ "box_id": 1, "uniqueId": "1", "commandType": "StartGameCommand" }`
  - possible answers:
    - `{ "uniqueId": "1", "commandType": "StartGameCommand", "status": "success" }`
    - `{ "uniqueId": "1", "commandType": "StartGameCommand", "status": "error", "message": "Not enough players" }`
    - `{ "uniqueId": "1", "commandType": "StartGameCommand", "status": "error", "message": "Box not connected" }`
    - `{ "uniqueId": "1", "commandType": "StartGameCommand", "status": "error", "message": "Game already started" }`
- Start voting *broadcasted to all remotes*:
  - command:`{ "box_id": 1, "uniqueId": "1", "commandType": "StartVoting" }`
  - possible answers:
    - `{ "uniqueId": "1", "commandType": "StartVoting", "status": "success" }`
    - `{ "uniqueId": "1", "commandType": "StartVoting", "status": "error", "message": "Game not started" }`
    - `{ "uniqueId": "1", "commandType": "StartVoting", "status": "error", "message": "Game already in voting state" }`
- Stopping a game *broadcasted to all remotes*:
  - command:`{ "box_id": 1,"uniqueId": "1", "commandType": "StopGameCommand" }`
  - possible answers:
    - `{ "uniqueId": "1", "commandType": "StopGameCommand", "status": "success" }`
    - `{ "uniqueId": "1", "commandType": "StopGameCommand", "status": "error", "message": "Game not started" }`
    - `{ "uniqueId": "1", "commandType": "StopGameCommand", "status": "error", "message": "Game already stopped" }`

### Remote

- Connection *broadcasted to all remotes*:
  - command: `{ box_id: "1", "uniqueId": "2", "commandType": "ConnectRemote" }`
  - possible answers:
    - `{ "uniqueId": "2", "commandType": "ConnectRemote", "status": "success" }`
    - `{ "uniqueId": "2", "commandType": "ConnectRemote", "status": "error", "message": "Box not connected" }`
    - `{ "uniqueId": "2", "commandType": "ConnectRemote", "status": "error", "message": "Remote already connected" }`
- Vote *broadcasted to all remotes*:
  - command: `{ box_id: "1", "uniqueId": "2","commandType": "VoteCommand", "vote": "yes" }`
  - possible answers:
    - `{ "uniqueId": "2","commandType": "VoteCommand", "status": "success" }`
    - `{ "uniqueId": "2","commandType": "VoteCommand", "status": "error", "message": "Game not in voting state" }`
    - `{ "uniqueId": "2","commandType": "VoteCommand", "status": "error", "message": "Remote not connected" }`
    - `{ "uniqueId": "2","commandType": "VoteCommand", "status": "error", "message": "Invalid vote" }`
    - `{ "uniqueId": "2","commandType": "VoteCommand", "status": "error", "message": "Vote already cast" }`
- Disconnection:
  - command: `{ box_id: "1", "uniqueId": "2", "commandType": "DisconnectRemote" }`
  - possible answers:
    - `{ "uniqueId": "2", "commandType": "DisconnectRemote", "status": "success" }`
    - `{ "uniqueId": "2", "commandType": "DisconnectRemote", "status": "error", "message": "Remote not connected" }`

## Testing

To test the server you can use the python server located in `server_2/python-client`

## Game logic

The Anectdot game is a multiplayer game managed by a central box (the `RemoteManager` actor) and controlled by individual remote devices, each represented as a `Remote` actor. The central box is responsible for managing the game states and coordinating the voting process. This setup allows players to join a session and participate in voting phases. The following section describes the core game logic and commands used to manage game flow and player interactions.

### Game States

The game has several distinct states:

1. **STOPPED**: The default state; no game is active.
2. **STARTED**: The game has begun, allowing player interactions.
3. **PAUSED**: The game is temporarily paused.
4. **VOTING**: Players are allowed to vote on outcomes within the game.

The game state determines which actions are allowed. For example, voting can only occur in the VOTING state.

### Commands and Game Flow

The `RemoteManager` handles various commands to manage game interactions, as detailed below.

#### Start the Game (`StartGameCommand`)

1. **Conditions**: A game can only start if:
   - A box is connected.
   - There are at least 2 connected players (`MIN_PLAYERS`).
2. **Actions**:
   - Sets the game state to `STARTED`.
   - Broadcasts the new game state to all connected clients.

#### Stop the Game (`StopGameCommand`)

1. **Conditions**: The game must be in either `STARTED` or `PAUSED` state to stop.
2. **Actions**:
   - Clears all connected remotes and resets scores.
   - Sets the game state to `STOPPED`.
   - Broadcasts the game’s stopping state to all connected clients.

#### Connect the Box (`ConnectBox`)

1. **Conditions**: Only one box can be connected at a time.
2. **Actions**:
   - Registers the box in the game session.
   - Notifies all clients that the box is connected.

#### Connect a Remote (`ConnectRemote`)

1. **Conditions**:
   - The box must be connected.
   - The game must be in the `STARTED` state.
   - The remote must not already be connected.
2. **Actions**:
   - Adds the remote to the active player list.
   - Notifies clients of the new remote’s connection status.

#### Disconnect a Remote (`DisconnectRemote`)

1. **Conditions**: The remote must be already connected.
2. **Actions**:
   - Removes the remote from the player list.
   - Notifies clients of the disconnection.

#### Start Voting Phase (`StartVoting`)

1. **Conditions**: Voting can only begin when the game is in the `STARTED` state.
2. **Actions**:
   - Sets the game state to `VOTING`.
   - Notifies all clients of the voting phase.

#### Cast a Vote (`VoteCommand`)

1. **Conditions**: Voting is allowed only when the game is in the `VOTING` state.
2. **Actions**:
   - Registers the vote and broadcasts it to all clients.
   - Increments the `voteNumber`. Once all connected remotes have voted, the game state returns to `STARTED`.
   - Updates the score of the remote that cast the vote.

### Helper Methods

#### `broadcastGameState`

This method notifies all connected clients of the current game state to ensure they stay synchronized with the central box.

#### `broadcastVote`

This method broadcasts each vote to all connected clients, allowing them to track the voting phase progress.

### Typical Game Flow

#### 1. **Setup Phase**

- **Box Connection**: The central game box (handled by `RemoteManager`) must be connected first. A `ConnectBox` command is sent to the `RemoteManager`, which registers the box as active.
- **Client Notification**: Once the box is connected, clients are notified that the game box is ready for connections.

#### 2. **Player Registration**

- **Remote Connections**: Players connect their remote devices to the game by sending `ConnectRemote` commands. Each connection is assigned a unique `remote_id` by the `RemoteManager`.
- **Minimum Players Check**: To begin the game, a minimum of 2 players must be connected. The `RemoteManager` keeps track of the connected players through `remoteActors`.

#### 3. **Game Start**

- **Start Game Command**: Once the minimum player count is met, a `StartGameCommand` can be issued by the game administrator or box.
- **State Change**: The `RemoteManager` verifies that the box is connected and that there are enough players, then changes the `game_state` to `STARTED`.
- **Broadcast**: All clients are notified that the game has started, allowing players to begin participating.

#### 4. **Gameplay and Interactions**

- **Gameplay State**: While in the `STARTED` state, players can interact with the game as defined by its specific rules (e.g., answering questions, performing actions).
- **Real-Time Updates**: During gameplay, the `RemoteManager` can provide real-time updates to all connected clients about the current game state or any important events.

#### 5. **Voting Phase**

- **Initiate Voting**: At a designated point in the game, a `StartVoting` command is issued to enter the voting phase. This phase might be used for players to vote on a particular outcome (e.g., determining a round winner).
- **State Change**: The `RemoteManager` sets `game_state` to `VOTING`, signaling all players that voting is now active.
- **Vote Collection**: Each connected player can cast their vote through a `VoteCommand`. The system broadcasts each vote to all players as they are received.
- **Vote Tallying**: Once all players have voted (tracked by `voteNumber` matching the number of connected remotes), voting ends, and the game returns to the `STARTED` state.

#### 6. **Game Stopping or Pausing**

- **Pause or Stop Command**: At any point, the game can be paused or stopped by issuing `PauseGameCommand` or `StopGameCommand`.
- **Stop Conditions**: The game can only be stopped if it is in the `STARTED` or `PAUSED` states. When stopped, all player connections are cleared, and scores are reset.
- **Game Over Notification**: All clients are notified of the game’s end or pause, and if stopped, a winner or final result can be broadcasted (this is a pending feature in the current implementation).

#### 7. **Results and Cleanup**

- **Score Announcement**: At the end of the game, scores or final results can be announced to all connected players. This step involves calculating final scores or designating a winner.
- **Cleanup**: The `RemoteManager` clears all connected players and resets relevant variables, returning to the `STOPPED` state and preparing for the next game session.

---

### Summary of Game Lifecycle Commands

1. **Setup**: `ConnectBox`
2. **Player Registration**: `ConnectRemote`
3. **Start Game**: `StartGameCommand`
4. **Gameplay**: Ongoing interactions in `STARTED` state
5. **Voting**: `StartVoting` and `VoteCommand`
6. **Pause/Stop**: `PauseGameCommand` or `StopGameCommand`
7. **Cleanup**: Score calculation, announcements, reset of state variables

       +----------------+
       |   STOPPED      |
       +----------------+
              |
              | ConnectBox command
              |
              v
       +----------------+
       |   READY        |
       +----------------+
              |
              | ConnectRemote (until min players)
              |
              v
       +----------------+
       |   STARTED      |
       +----------------+
              |
              | StartVoting command
              |
              v
       +----------------+
       |   VOTING       |
       +----------------+
              |
              | All votes received
              |
              v
       +----------------+
       |   STARTED      |
       +----------------+
              |
              | StopGame or PauseGame command
              |
              v
       +----------------+
       | STOPPED/PAUSED |
       +----------------+
