# hotspoter

A simple script to create a Wi-Fi hotspot on a Raspberry Pi that allows users to connect the Pi to a Wi-Fi network without needing a monitor or keyboard.

## Installation and Usage

1. Clone the repository
2. Run the following command to run the setup script:

    ```bash
    sudo ./setup.sh
    ```

3. Reboot
4. Connect to the hotspot using another device like a phone or another computer
5. Open a browser and navigate to `http://anec.fun` (or on Android click the notification that says "Sign in to Wi-Fi network")
6. Enter the SSID and password for the network you want to connect to

## Structure of hotspoter

- `setup.sh` - The setup script for the project
- `app.py` - Flutter app that creates the web interface for the hotspot
- `check_wifi.sh` - Script that checks if the device is connected to a Wi-Fi network and if not, starts the hotspot
- `check_wifi.service` - Systemd service file that runs the `check_wifi.sh` script on boot
- `hostapd.conf` - Configuration file for the hostapd service

## TODO

- uncomment captive portal code and do a prescan before starting the hotspot as this should be enough for our purposes (while still offering manual scan just in case)
