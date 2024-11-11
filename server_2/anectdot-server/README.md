# Anectdot Server

Welcome to the Anectdot Server project! This README will guide you through the setup and usage of the server.

## Table of Contents

- [Anectdot Server](#anectdot-server)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Installation](#installation)
  - [Usage](#usage)
  - [Testing](#testing)

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

## Testing

To test the server you can use the python server located in `server_2/python-client`
