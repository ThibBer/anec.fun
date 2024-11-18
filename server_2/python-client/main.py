import asyncio
import websockets
import json


class WebSocketClient:
    def __init__(self, box_id, commands):
        self.box_id = box_id
        self.commands = commands
        self.unique_id = ""

    async def connect(self):
        uri = f"ws://localhost:8080/ws/{self.box_id}"
        async with websockets.connect(uri) as websocket:
            await self.handle_connection(websocket)

    async def handle_connection(self, websocket):
        # Initial handshake to get uniqueId
        initial_response = await self.receive_message(websocket)
        self.unique_id = initial_response.get("uniqueId", "")
        print(f"Connected with uniqueId: {self.unique_id}")

        # Update commands with uniqueId
        for command in self.commands:
            command["uniqueId"] = self.unique_id

        # Execute commands
        for command in self.commands:
            await self.execute_command(command, websocket)

    async def execute_command(self, command, websocket):
        await self.send_message(command, websocket)
        response = await self.receive_message(websocket)

        if self.is_failed_response(response, command):
            print(f"Command failed, retrying: {response}")
            await asyncio.sleep(3)
            await self.execute_command(command, websocket)
        else:
            print(f"Command succeeded: {response}")

    async def send_message(self, message, websocket):
        json_message = json.dumps(message)
        await websocket.send(json_message)
        print(f"Sent: {json_message}")

    async def receive_message(self, websocket):
        response = await websocket.recv()
        return json.loads(response)

    @staticmethod
    def is_failed_response(response, command):
        """Determine if a response indicates failure."""
        return response.get("status") == "failed" or response.get(
            "commandType"
        ) != command.get("commandType")


async def main():
    # Box and remotes commands
    box_commands = [
        {"boxId": 2, "uniqueId": "", "commandType": "ConnectBox"},
        {"boxId": 2, "uniqueId": "", "commandType": "StartGameCommand"},
        {"boxId": 2, "uniqueId": "", "commandType": "StartVoting"},
    ]
    remote_1_commands = [
        {"boxId": 2, "uniqueId": "", "commandType": "ConnectRemote"},
        {"boxId": 2, "uniqueId": "", "vote": "no", "commandType": "VoteCommand"},
    ]
    remote_2_commands = [
        {"boxId": 2, "uniqueId": "", "commandType": "ConnectRemote"},
        {"boxId": 2, "uniqueId": "", "vote": "yes", "commandType": "VoteCommand"},
    ]

    # Create clients for the box and remotes
    tasks = [
        WebSocketClient(2, box_commands).connect(),
        # WebSocketClient(2, remote_1_commands).connect(),
        # WebSocketClient(2, remote_2_commands).connect(),
    ]

    await asyncio.gather(*tasks)


asyncio.run(main())
