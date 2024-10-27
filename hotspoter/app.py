"""Provide a web interface to connect to Wi-Fi networks."""

import subprocess
import threading
from time import sleep

from flask import (
    Flask,
    render_template,
    request,
)
from flask_socketio import SocketIO

app = Flask(__name__)

networks: list[dict[str, str]] = []
scan_duration = 10


# The following code trigger the "sign in notification on android"
# but this automatically removes the webpage when the scan is triggered
# @app.route("/generate_204")
# @app.route("/ncsi.txt")
# @app.route("/hotspot-detect.html")
# def captive_portal_redirect():
#     # Redirect to the captive portal page
#     return redirect("/")


@app.route("/", methods=["GET", "POST"])
def index() -> str:
    """Render the index page with the network cards."""
    return render_template("index.html", cards=networks)


@app.route("/connect", methods=["GET", "POST"])
def connect() -> str:
    """Render the connect page with the form to input Wi-Fi credentials."""
    if request.method == "POST":
        ssid = request.form["ssid"]
        psk = request.form["password"]
        configure_wifi(ssid, psk)
        return "Wi-Fi credentials saved! Hotspot stopped and connecting to wifi..."
    ssid = request.args.get("ssid", "")
    return render_template("connect.html", ssid=ssid)


def network_scan() -> None:
    """Function to generate cards from WiFi scan results."""
    cards = []
    try:
        subprocess.run(["sudo", "systemctl", "stop", "hostapd"])
        subprocess.run(["sudo", "systemctl", "start", "NetworkManager"])
        subprocess.run(["sudo", "systemctl", "start", "wpa_supplicant"])
        i = scan_duration
        while i > 0:
            subprocess.run(["nmcli", "dev", "wifi", "rescan"])
            sleep(1)
            i -= 1
        # Run nmcli to scan WiFi and retrieve the output
        result = subprocess.check_output(
            ["nmcli", "-f", "SSID,SIGNAL", "dev", "wifi"], text=True
        )

        lines = result.splitlines()[1:]  # Skip the header line

        # Process each line to separate SSID and SIGNAL accurately
        for line in lines:
            # Use rsplit to split the last whitespace segment (signal strength) from SSID
            ssid, signal = line.rsplit(maxsplit=1)
            cards.append(
                {
                    "title": f"Network: {ssid.strip()}",
                    "description": f"Signal Strength: {signal.strip()}%",
                    "button_text": "Connect",
                    "button_link": f"/connect?ssid={ssid.strip()}",
                }
            )
    except subprocess.CalledProcessError as e:
        print("Failed to scan WiFi networks:", e)

    # send the cards back to the client
    global networks
    networks = cards
    socketio.emit("task_complete", {"result": cards})

    subprocess.run(["sudo", "systemctl", "start", "hostapd"])
    subprocess.run(["sudo", "systemctl", "stop", "NetworkManager"])
    subprocess.run(["sudo", "systemctl", "stop", "wpa_supplicant"])


@app.route("/refresh_cards")
def refresh_cards() -> str:
    """Refresh the network cards."""
    # start a thread to scan networks
    thread = threading.Thread(target=network_scan)
    thread.start()
    return str(scan_duration)


def configure_wifi(ssid: str, psk: str) -> None:
    """Configure Wi-Fi connection using nmcli."""
    # Configure services
    subprocess.run(["sudo", "systemctl", "stop", "hostapd"])
    subprocess.run(["sudo", "systemctl", "start", "NetworkManager"])
    subprocess.run(["sudo", "systemctl", "start", "wpa_supplicant"])
    print("Waiting for services to start...")
    subprocess.run(["sleep", "5"])

    # Trigger a Wi-Fi scan
    print("Scanning for available networks...")
    subprocess.run(["nmcli", "device", "wifi", "rescan"])

    # Wait for a short moment to ensure scan results are available
    print("Waiting for scan to complete...")
    subprocess.run(["sleep", "5"])  # You can adjust the sleep time if necessary

    subprocess.run(
        ["sudo", "nmcli", "device", "wifi", "connect", ssid, "password", psk]
    )
    print(f"Wi-Fi connection for SSID {ssid} configured and applied.")


# Initialize Flask-SocketIO
socketio = SocketIO(app)

if __name__ == "__main__":
    socketio.run(app, host="0.0.0.0", port=80, allow_unsafe_werkzeug=True)
