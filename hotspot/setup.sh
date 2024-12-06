#!/bin/bash

set -e  # Exit script if any command fails
# Ensure the script is run as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run as root"
    exit
fi

# Define variables
FLASK_APP_DIR="/usr/local/etc/hotspoter"
WIFI_CHECK_SCRIPT="/usr/local/bin"

# Step 1: Install required packages
echo "Installing required packages..."
sudo apt update
sudo apt install -y hostapd dnsmasq python3-flask nftables dhcpcd

# Step 8: Set up Flask web server for Wi-Fi configuration
echo "Setting up Flask web server..."
sleep 5
mkdir -p $FLASK_APP_DIR
sudo cp app.py $FLASK_APP_DIR/app.py
sudo cp -r templates $FLASK_APP_DIR
sudo cp -r static $FLASK_APP_DIR
python3 -m venv $FLASK_APP_DIR/.venv
source $FLASK_APP_DIR/.venv/bin/activate
pip install flask
pip install flask_socketio

# Step 2: Configure static IP for wlan0
echo "Configuring static IP for wlan0..."
sudo tee -a /etc/dhcpcd.conf > /dev/null <<EOL
interface wlan0
    static ip_address=192.168.4.1/24
    nohook wpa_supplicant
EOL
sudo systemctl restart dhcpcd

## Step 2.1: add custom dns record
echo "Adding dns record..."
if ! grep -q "192.168.4.1    anec.fun" /etc/hosts; then
    sudo tee -a /etc/hosts > /dev/null <<EOL
192.168.4.1    anec.fun
EOL
else
    echo "DNS record already exists."
fi

# Step 3: Configure dnsmasq
echo "Configuring dnsmasq..."
sudo mv /etc/dnsmasq.conf /etc/dnsmasq.conf.orig || true
sudo tee /etc/dnsmasq.conf > /dev/null <<EOL
interface=wlan0
dhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h
address=/#/192.168.4.1
EOL

# Step 4: Configure hostapd
echo "Configuring hostapd..."
cp hostapd.conf /etc/hostapd/hostapd.conf

# Step 5: Enable IP forwarding
echo "Enabling IP forwarding..."
if [ ! -f /etc/sysctl.conf ]; then
    echo "/etc/sysctl.conf not found. Creating a new file..."
    sudo touch /etc/sysctl.conf
    echo "# Sysctl configuration file" | sudo tee /etc/sysctl.conf
fi

sudo sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf
if ! grep -q "net.ipv4.ip_forward=1" /etc/sysctl.conf; then
    echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf
fi
sudo sysctl -p

# Step 6: Configure NAT with nftables
echo "Configuring NAT with nftables..."
sudo tee /etc/nftables.conf > /dev/null <<EOL
table ip nat {
    chain prerouting {
        type nat hook prerouting priority 0; policy accept;
        tcp dport 80 dnat to 192.168.4.1:80
	tcp dport 443 dnat to 192.168.4.1:443
    }

    chain postrouting {
        type nat hook postrouting priority 100; policy accept;
        oifname "eth0" masquerade
    }
}

table inet filter {
    chain input {
        type filter hook input priority 0; policy accept;
        tcp dport { 80, 5000 } accept
	tcp dport { 443, 5000 } accept
    }
}
EOL

# Apply the nftables configuration
sudo nft -f /etc/nftables.conf

# Make sure nftables starts on boot
sudo systemctl enable nftables
sudo systemctl restart nftables

# Step 7: Start hostapd and dnsmasq
echo "Starting hostapd and dnsmasq..."
sudo systemctl unmask hostapd
#sudo systemctl enable hostapd
sudo systemctl enable dnsmasq
sudo systemctl start hostapd
sudo systemctl start dnsmasq

# Step 9: Create Wi-Fi check script to disable hotspot after Wi-Fi connection
echo "Creating Wi-Fi check script..."
sudo cp check_wifi.sh $WIFI_CHECK_SCRIPT
sudo chmod +x $WIFI_CHECK_SCRIPT/check_wifi.sh

# Step 10: Create systemd service for the Wi-Fi check script
echo "Creating systemd service for Wi-Fi check..."
cp check_wifi.service /etc/systemd/system/check_wifi.service

sudo systemctl daemon-reload
sudo systemctl enable check_wifi.service
sudo systemctl start check_wifi.service

echo "Wi-Fi Hotspot and Captive Portal setup is complete. Reboot your Raspberry Pi to apply the changes."
