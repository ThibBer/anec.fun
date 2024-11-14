import asyncio
import websockets
import json


async def start_box_interaction(commands: list[dict[str, str]]):
    box_id = commands[0].get("box_id")
    uri = f"ws://localhost:8080/ws/{box_id}"

    async with websockets.connect(uri) as websocket:
        response = await websocket.recv()
        response = json.loads(response)
        print(f"Received: {response}")
        uniqueId = response.get("uniqueId")
        # replace uniqueId in commands
        for command in commands:
            command["uniqueId"] = uniqueId
        for command in commands:
            await send_command(command, websocket)

        await websocket.close()


async def send_command(
    command: dict[str, str], websocket: websockets.WebSocketClientProtocol
):
    message = json.dumps(command)
    await websocket.send(message)
    print(f"Sent: {message}")

    response = await websocket.recv()
    response = json.loads(response)
    if response.get("status") == "failed":
        print(f"Received: {response}")
        # sleep and retry
        await asyncio.sleep(1)
        await send_command(command, websocket)
    print(f"Received: {response}")


async def main():
    box_commands = [
        {"box_id": 2, "uniqueId": "", "commandType": "ConnectBox"},
        {"box_id": 2, "uniqueId": 1, "commandType": "StartGameCommand"},
        {"box_id": 2, "uniqueId": 1, "commandType": "StartVoting"},
    ]
    remote_1_commands = [
        {"box_id": 2, "uniqueId": "", "commandType": "ConnectRemote"},
        {"box_id": 2, "uniqueId": 1, "vote": "no", "commandType": "VoteCommand"},
    ]
    remote_2_commands = [
        {"box_id": 2, "uniqueId": "", "commandType": "ConnectRemote"},
        {"box_id": 2, "uniqueId": 1, "vote": "no", "commandType": "VoteCommand"},
    ]

    tasks = [
        start_box_interaction(box_commands),
        start_box_interaction(remote_1_commands),
        start_box_interaction(remote_2_commands),
    ]

    await asyncio.gather(*tasks)


asyncio.run(main())
