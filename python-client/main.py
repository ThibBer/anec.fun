import asyncio
import websockets
import json

box_id = 1


class WebSocketClient:
    def __init__(self, box_id, commands):
        self.box_id = box_id
        self.commands = commands
        self.unique_id = ""
        self.websocket = None  # Store the WebSocket connection

    async def connect(self):
        uri = f"ws://localhost:8080/ws/{self.box_id}"
        async with websockets.connect(uri) as websocket:
            self.websocket = websocket
            await asyncio.gather(self.handle_connection(), self.send_heartbeat())

    async def handle_connection(self):
        # Initial handshake to get uniqueId
        initial_response = await self.receive_message()
        self.unique_id = initial_response.get("uniqueId", "")
        print(f"Connected with uniqueId: {self.unique_id}")

        # Update commands with uniqueId
        for command in self.commands:
            command["uniqueId"] = self.unique_id

        # Execute commands
        for command in self.commands:
            print(f"Ready to execute command: {command}")
            input("Press Enter to continue...")
            await self.execute_command(command)

    async def send_heartbeat(self):
        """Send a heartbeat message every 5 seconds."""
        try:
            while True:
                heartbeat_message = "heartbeat"
                await self.send_message(heartbeat_message, msg_type="heartbeat")
                await asyncio.sleep(5)
        except websockets.ConnectionClosed as e:
            print(f"Heartbeat stopped due to connection closure: {e}")
        except asyncio.CancelledError:
            print("Heartbeat task cancelled")

    async def execute_command(self, command):
        await self.send_message(command)
        response = await self.receive_message()

        if self.is_failed_response(response, command):
            print(f"Command failed, retrying: {response}")
        else:
            print(f"Command succeeded: {response}")

    async def send_message(self, message, msg_type="command"):
        if msg_type == "command":
            json_message = json.dumps(message)
        else:
            json_message = message
        try:
            await self.websocket.send(json_message)
            print(f"Sent: {json_message}")
        except websockets.ConnectionClosed as e:
            print(f"Failed to send message: {e}")
            raise

    async def receive_message(self):
        try:
            response = await self.websocket.recv()
            return json.loads(response)
        except websockets.ConnectionClosed as e:
            print(f"Failed to receive message: {e}")
            raise

    @staticmethod
    def is_failed_response(response, command):
        """Determine if a response indicates failure."""
        return response.get("status") == "failed" or response.get(
            "commandType"
        ) != command.get("commandType")


async def main():
    # Box and remotes commands
    box_commands = [
        {"boxId": box_id, "uniqueId": "", "commandType": "ConnectBox"},
        {"boxId": box_id, "uniqueId": "", "commandType": "StartGameCommand"},
        {"boxId": box_id, "uniqueId": "", "commandType": "StartRoundCommand"},
    ]

    # Create clients for the box
    tasks = [WebSocketClient(box_id, box_commands).connect()]

    await asyncio.gather(*tasks)


asyncio.run(main())
