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
#sudo apt update
#sudo apt install -y hostapd dnsmasq python3-flask

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
if ! grep -q "192.168.4.1    anectdot.fun" /etc/hosts; then
    sudo tee -a /etc/hosts > /dev/null <<EOL
192.168.4.1    anectdot.fun
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

# Check if /etc/sysctl.conf exists, and create it if necessary
if [ ! -f /etc/sysctl.conf ]; then
    echo "/etc/sysctl.conf not found. Creating a new file..."
    sudo touch /etc/sysctl.conf
    echo "# Sysctl configuration file" | sudo tee /etc/sysctl.conf
fi

# Modify sysctl.conf to enable IP forwarding
sudo sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf

# If the line doesn't exist at all, append it to the file
if ! grep -q "net.ipv4.ip_forward=1" /etc/sysctl.conf; then
    echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf
fi

# Apply the changes
sudo sysctl -p


# Step 6: Configure NAT with iptables
echo "Configuring NAT with iptables..."
sudo iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
sudo sh -c "iptables-save > /etc/iptables.ipv4.nat"
sudo tee -a /etc/rc.local > /dev/null <<EOL
iptables-restore < /etc/iptables.ipv4.nat
EOL

sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j DNAT --to-destination 192.168.4.1:80
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT


# Step 7: Start hostapd and dnsmasq
echo "Starting hostapd and dnsmasq..."
sudo systemctl unmask hostapd
sudo systemctl enable hostapd
sudo systemctl enable dnsmasq
sudo systemctl start hostapd
sudo systemctl start dnsmasq

# Step 8: Set up Flask web server for Wi-Fi configuration
echo "Setting up Flask web server..."
sudo cp app.py $FLASK_APP_DIR/app.py
sudo cp -r templates $FLASK_APP_DIR
sudo cp -r static $FLASK_APP_DIR
python3 -m venv $FLASK_APP_DIR/.venv
source $FLASK_APP_DIR/.venv/bin/activate
pip install flask
pip install flask_socketio

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

# other
sudo iptables -A INPUT -p tcp --dport 5000 -j ACCEPT