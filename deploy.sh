#!/bin/bash

# Deploy script for Lia Raskraska Android app
# Deploys APK to a physical device connected via WiFi or USB

set -e

echo "=== Deploying Lia Raskraska Android App ==="

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    # Try common locations
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "Error: ANDROID_HOME is not set. Please set it to your Android SDK location."
        exit 1
    fi
fi

# Add platform-tools to PATH
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Navigate to project directory
cd "$(dirname "$0")"

# Function to connect via WiFi
connect_wifi() {
    local ip="$1"
    local port="${2:-5555}"
    
    echo "Connecting to device at $ip:$port..."
    adb connect "$ip:$port"
    sleep 2
}

# Function to setup WiFi debugging from USB connection
setup_wifi_from_usb() {
    echo "Setting up WiFi debugging from USB connection..."
    
    # Get the USB device serial
    USB_SERIAL=$(adb devices | grep -v "List" | grep -v "^$" | grep -v ":5555" | awk '{print $1}' | head -1)
    
    if [ -z "$USB_SERIAL" ]; then
        echo "Error: No USB device found. Connect device via USB first."
        exit 1
    fi
    
    echo "USB device: $USB_SERIAL"
    
    # Get device IP address
    DEVICE_IP=$(adb -s "$USB_SERIAL" shell ip route | awk '{print $9}' | head -1)
    
    if [ -z "$DEVICE_IP" ]; then
        echo "Error: Could not determine device IP. Make sure device is connected to WiFi."
        exit 1
    fi
    
    echo "Device IP: $DEVICE_IP"
    
    # Enable tcpip mode
    echo "Enabling TCP/IP mode on port 5555..."
    adb -s "$USB_SERIAL" tcpip 5555
    sleep 2
    
    # Connect via WiFi
    echo "Connecting via WiFi..."
    adb connect "$DEVICE_IP:5555"
    sleep 3
    
    # Disconnect USB device from adb to avoid "more than one device" errors
    echo "Disconnecting USB device from adb..."
    adb disconnect "$USB_SERIAL" 2>/dev/null || true
    
    # Store the WiFi device for subsequent commands
    TARGET_DEVICE="$DEVICE_IP:5555"
    
    echo ""
    echo "WiFi debugging enabled. You can now disconnect the USB cable."
    echo "To reconnect later, run: ./deploy.sh --connect $DEVICE_IP"
}

# Config file for storing device mode
CONFIG_FILE="$(dirname "$0")/.deploy_config"

# Function to save config
save_config() {
    local mode="$1"
    local device="$2"
    echo "MODE=$mode" > "$CONFIG_FILE"
    echo "DEVICE=$device" >> "$CONFIG_FILE"
    echo "Saved config: mode=$mode, device=$device"
}

# Function to load config
load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        source "$CONFIG_FILE"
    fi
}

# Parse arguments
SKIP_BUILD=false
CONNECT_IP=""
SET_MODE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --wifi)
            SET_MODE="wifi"
            shift
            ;;
        --emulator)
            SET_MODE="emulator"
            shift
            ;;
        --connect|-c)
            CONNECT_IP="$2"
            shift 2
            ;;
        --setup-wifi|-w)
            setup_wifi_from_usb
            # Save the WiFi device to config
            save_config "wifi" "$DEVICE_IP:5555"
            echo ""
            echo "Config saved. Run ./run.sh to deploy to this device."
            exit 0
            ;;
        --skip-build|-s)
            SKIP_BUILD=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./deploy.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --wifi                Switch to WiFi device mode (uses last connected WiFi device)"
            echo "  --emulator            Switch to emulator mode"
            echo "  --connect, -c <IP>    Connect to device at specified IP address"
            echo "  --setup-wifi, -w      Setup WiFi debugging from USB connection"
            echo "  --skip-build, -s      Skip building, just install existing APK"
            echo "  --help, -h            Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./deploy.sh --setup-wifi       # Setup WiFi debugging (requires USB first)"
            echo "  ./deploy.sh --wifi             # Switch to WiFi mode for subsequent ./run.sh"
            echo "  ./deploy.sh --emulator         # Switch to emulator mode for subsequent ./run.sh"
            echo "  ./deploy.sh -c 192.168.1.100   # Connect to device and deploy"
            echo "  ./deploy.sh -s                 # Deploy existing APK without rebuilding"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Handle mode switching
if [ -n "$SET_MODE" ]; then
    if [ "$SET_MODE" = "wifi" ]; then
        # Find WiFi device
        WIFI_DEVICE=$(adb devices | grep ":5555" | grep "device$" | awk '{print $1}' | head -1)
        if [ -z "$WIFI_DEVICE" ]; then
            echo "No WiFi device currently connected."
            echo "Run ./deploy.sh --setup-wifi first with USB connected."
            exit 1
        fi
        save_config "wifi" "$WIFI_DEVICE"
        echo "Switched to WiFi mode. Device: $WIFI_DEVICE"
    elif [ "$SET_MODE" = "emulator" ]; then
        save_config "emulator" ""
        echo "Switched to emulator mode."
    fi
    exit 0
fi

# Connect to specified IP if provided
if [ -n "$CONNECT_IP" ]; then
    connect_wifi "$CONNECT_IP"
fi

# Check for connected devices
echo ""
echo "Checking for connected devices..."
adb devices -l

DEVICES=$(adb devices | grep -E "device$|device " | grep -v "List" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo ""
    echo "Error: No devices connected."
    echo ""
    echo "To connect a device:"
    echo "  1. Via USB: Connect device and enable USB debugging"
    echo "  2. Via WiFi (from USB): ./deploy.sh --setup-wifi"
    echo "  3. Via WiFi (direct): ./deploy.sh --connect <DEVICE_IP>"
    echo ""
    echo "For WiFi debugging, ensure:"
    echo "  - Device and computer are on the same network"
    echo "  - Developer options > Wireless debugging is enabled (Android 11+)"
    echo "  - Or use --setup-wifi with USB connected first"
    exit 1
fi

# Build the app
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo "Building the app..."
    ./build.sh
else
    echo ""
    echo "Skipping build..."
    if [ ! -f "$APK_PATH" ]; then
        echo "Error: APK not found at $APK_PATH. Run without --skip-build first."
        exit 1
    fi
fi

# Determine target device (prefer WiFi connection)
if [ -n "$TARGET_DEVICE" ]; then
    ADB_TARGET="-s $TARGET_DEVICE"
elif [ -n "$CONNECT_IP" ]; then
    ADB_TARGET="-s $CONNECT_IP:5555"
else
    # If multiple devices, prefer WiFi (port 5555)
    WIFI_DEVICE=$(adb devices | grep ":5555" | grep "device$" | awk '{print $1}' | head -1)
    if [ -n "$WIFI_DEVICE" ]; then
        ADB_TARGET="-s $WIFI_DEVICE"
    else
        ADB_TARGET=""
    fi
fi

# Install the APK
echo ""
echo "Installing APK..."
adb $ADB_TARGET install -r "$APK_PATH"

# Launch the app
echo ""
echo "Launching app..."
adb $ADB_TARGET shell am start -n com.lia.raskraska/.ui.MainActivity

echo ""
echo "=== Deployment Complete ==="
echo "App is now running on your device."
echo "To view logs: adb logcat -s LiaRaskraska:*"
