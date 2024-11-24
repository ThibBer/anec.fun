# Anecdot

## Table of Contents

- [Anecdot](#anecdot)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Gameplay](#gameplay)
  - [Project structure](#project-structure)
  - [Setup](#setup)
  - [Sequence diagram](#sequence-diagram)

## Introduction

Anecdot is a multiplayer game where players take turns telling anecdotes about a particular theme. The game is played with a box that serves as a central hub and remote devices that players use to interact with the game.

## Gameplay

To play the game, players need to:

1. Connect the box to internet by connecting to the hotspot
2. Connect their remote device to the box by using the app
3. Long press the start button on the box to start the game
4. Pass the stick to each other and tap the stick to their phone to validate their turn
5. The player that has the stick when the timer runs out must tell an anecdote about the theme
6. Everyone must submit their vote to tell if the anecdote is true or false
7. Then the box will announce the scores
8. The players can either push the next turn button to continue the game or the stop button to end the game

## Project structure

The project is structured as follows:

- box : The arduino code that runs on the box.
- hotspot : The bash script that setup a hotspot on the box.
- microphone : The arduino code that is used to record audio from the stick.
- mobile-app : The flutter mobile app that players use to interact with the game.
- python-client : Python client that tests the server.
- server : The scala server using akka to manage the game.
- stick : The arduino code that runs on the stick.

## Setup

To set up the game, you need to:

1. Clone the repository
2. Compile the project
3. Run the server
4. Connect the box to the server
5. Connect the remotes to the box

## Sequence diagram

```mermaid
sequenceDiagram
    %% Define participants
    participant A as Stick
    participant B as Box
    participant S as Server
    participant T1 as Remote 1
    participant T2 as Remote 2

    %% Connecting Box
    rect rgb(191, 223, 255)
    Note over B,S: Box connects to the server
    B->>S: ConnectBox
    S-->>B: ConnectBox
    end

    %% Connecting Remotes
    rect rgb(223, 255, 191)
    Note over T1,S: Remotes connect to the server
    T1->>S: ConnectRemote
    S-->>T1: ConnectRemote
    S-->>B: ConnectRemote
    T2->>S: ConnectRemote
    S-->>T2: ConnectRemote
    S-->>B: ConnectRemote
    end

    %% Starting Game
    rect rgb(255, 223, 191)
    Note over B,S: Box sends a command to start the game
    B->>S: StartGameCommand
    S-->>B: StartGameCommand
    S-->>T1: StartGameCommand
    S-->>T2: StartGameCommand
    end

    %% Game Loop
    loop Until StopGame
        %% Stick passing
        rect rgb(191, 191, 255)
        par
            Note over T1,T2: Stick is passed between players
            S->>S: Timer (x seconds)
            Note over S,B: Timer runs and stick "explodes"
            S-->>T1: StickExploded
            S-->>T2: StickExploded
            S-->>B: StickExploded
        and
            loop Until user scan when StickExploded is received
                T1->>T1: ScanStick
                T2->>T2: ScanStick
            end
        end
        end

        %% Speaker Selection
        rect rgb(255, 191, 223)
        Note over S,T1: Select speaker after explosion
        T1->>S: SpeakerSelection
        S-->>T1: SpeakerSelected
        S-->>T2: SpeakerSelected
        S-->>B: SpeakerSelected
        end

        %% Voting process
        rect rgb(223, 191, 255)
        Note over B,A: Box instructs stick to start listening
        B->>A: StartListening
        A->>B: VoicePayload
        B->>S: VoiceFlow (Audio Data)
        Note over S: Start the voting process
        S-->>T1: StartVoting
        S-->>T2: StartVoting
        S-->>B: StartVoting

        par
            T1->>S: VoteCommand
            T2->>S: VoteCommand
            B->>S: VoteCommand
        end

        Note over S: Share voting results
        S-->>T1: VoteResult
        S-->>T2: VoteResult
        S-->>B: VoteResult
        end

        %% Next turn or stop game
        rect rgb(191, 255, 223)
        Note over B,S: Box sends the next turn command or stop command
        B->>S: NextTurnCommand
        S-->>B: NextTurnCommand
        S-->>T1: NextTurnCommand
        S-->>T2: NextTurnCommand
        B->>S: StopGameCommand
        S-->>B: StopGameCommand
        S-->>T1: StopGameCommand
        S-->>T2: StopGameCommand
        end
    end



```
