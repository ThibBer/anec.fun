from flask import Flask, render_template, request
import subprocess

app = Flask(__name__)


@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        ssid = request.form["ssid"]
        psk = request.form["password"]
        configure_wifi(ssid, psk)
        return "Wi-Fi credentials saved! Hotspot stopped and connecting to wifi..."
    return render_template("index.html")


def configure_wifi(ssid, psk):
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


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
