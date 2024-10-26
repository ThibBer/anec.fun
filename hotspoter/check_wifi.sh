#!/bin/bash

FLASK_APP_DIR="/usr/local/etc/hotspoter"

# Function to check if a service is active
is_active() {
    systemctl is-active --quiet "$1"
}

# Function to check if the Python app is running
is_python_app_running() {
    pgrep -f "python3 $FLASK_APP_DIR/app.py" > /dev/null
}

# Wait for up to 30 seconds for Wi-Fi to connect at boot
for _ in {1..6}; do
    if nmcli connection show --active | grep -q "wifi"; then
        echo "Wi-Fi connection established."
        break
    else
        echo "Waiting for Wi-Fi connection..."
        sleep 5
    fi
done

# Start hotspot if no WiFi connection
while true;
do
    if nmcli connection show --active | grep -q "wifi"; then
        echo "Wi-Fi connection found."

        # Check if hostapd is running, stop it if needed
        if is_active "hostapd"; then
            echo "Disabling hotspot..."
            sudo systemctl stop hostapd
        else
            echo "Hotspot is already stopped."
        fi

        # Ensure NetworkManager and wpa_supplicant are running
        if ! is_active "NetworkManager"; then
            sudo systemctl start NetworkManager
        else
            echo "NetworkManager is already running."
        fi

        if ! is_active "wpa_supplicant"; then
            sudo systemctl start wpa_supplicant
        else
            echo "wpa_supplicant is already running."
        fi

    else
        echo "No Wi-Fi connection found."

        # Check if NetworkManager is active, stop it if needed
        if is_active "NetworkManager"; then
            echo "Stopping NetworkManager..."
            sudo systemctl stop NetworkManager
        else
            echo "NetworkManager is already stopped."
        fi

        # Check if wpa_supplicant is active, stop it if needed
        if is_active "wpa_supplicant"; then
            echo "Stopping wpa_supplicant..."
            sudo systemctl stop wpa_supplicant
        else
            echo "wpa_supplicant is already stopped."
        fi

        # Check if hostapd is running, start it if needed
        if ! is_active "hostapd"; then
            echo "Starting hotspot..."
            sudo systemctl restart hostapd
        else
            echo "Hotspot is already running."
        fi

        # Check if the Python app is already running, start it if not
        if ! is_python_app_running; then
            echo "Starting Python app..."
            source $FLASK_APP_DIR/.venv/bin/activate
            python3 $FLASK_APP_DIR/app.py
        else
            echo "Python app is already running."
        fi
    fi

    sleep 5
done
