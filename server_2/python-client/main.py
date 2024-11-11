import asyncio
import websockets
import json


async def send_command(message, additional_messages=None, repeat=False):
    box_id = message.get("box_id")
    uri = f"ws://localhost:8080/ws/{box_id}"

    async with websockets.connect(uri) as websocket:
        await websocket.send(json.dumps(message))
        print(f"Sent: {message}")

        try:
            while True:
                response = await websocket.recv()
                print(f"Received: {response}")

                if additional_messages:
                    for msg in additional_messages:
                        await websocket.send(json.dumps(msg))
                        print(f"Sent: {msg}")

                if not repeat:
                    break

                await asyncio.sleep(5)
                await websocket.send(json.dumps(message))
                print(f"Sent: {message}")

        except websockets.ConnectionClosed:
            print("Connection closed by the server.")


async def main():
    start_message = {"box_id": 2, "commandType": "StartGameCommand"}
    vote_message = {"box_id": 2, "vote": "yes", "commandType": "VoteCommand"}
    connect_message_1 = {"box_id": 2, "remote_id": 1, "commandType": "ConnectRemote"}
    connect_message_2 = {"box_id": 2, "remote_id": 2, "commandType": "ConnectRemote"}
    additional_vote_message = {"box_id": 2, "vote": "no", "commandType": "VoteCommand"}

    tasks = [
        send_command(start_message),
        send_command(connect_message_1, additional_messages=[additional_vote_message]),
        send_command(connect_message_2, additional_messages=[additional_vote_message]),
        send_command(vote_message, repeat=True),
    ]

    await asyncio.gather(*tasks)


asyncio.run(main())
