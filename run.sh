#!/bin/bash

# Run script for Match-3 Tower Defense Android app
# Builds the APK and installs it on an emulator or connected device

set -e

REINSTALL_FIRST=false

usage() {
    echo "Usage: $0 [-r]"
    echo "  -r  Uninstall the app via adb before installing the new build"
}

while getopts ":r" opt; do
    case "$opt" in
        r)
            REINSTALL_FIRST=true
            ;;
        \?)
            usage
            exit 1
            ;;
    esac
done

shift $((OPTIND - 1))

echo "=== Running Match-3 Android Game ==="

if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME is not set. Please set it to your Android SDK location."
    exit 1
fi

echo "Using ANDROID_HOME: $ANDROID_HOME"

# Add platform-tools to PATH
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$ANDROID_HOME/emulator:$PATH"

# Navigate to project directory
cd "$(dirname "$0")"

# Config file for device mode
CONFIG_FILE="$(dirname "$0")/.deploy_config"
MODE=""
DEVICE=""
ADB_TARGET=""

# Load config if exists
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
    echo "Loaded config: mode=$MODE, device=$DEVICE"
fi

# Handle based on mode
if [ "$MODE" = "wifi" ]; then
    echo "Using WiFi mode..."
    if [ -n "$DEVICE" ]; then
        # Check if device is connected
        if ! adb devices | grep -q "$DEVICE.*device$"; then
            echo "WiFi device $DEVICE not connected. Attempting to reconnect..."
            adb connect "$DEVICE" 2>/dev/null || true
            sleep 2
        fi
        
        if adb devices | grep -q "$DEVICE.*device$"; then
            ADB_TARGET="-s $DEVICE"
            echo "Connected to WiFi device: $DEVICE"
        else
            echo "Error: Could not connect to WiFi device $DEVICE"
            echo "Run ./deploy.sh --setup-wifi to reconfigure."
            exit 1
        fi
    else
        # Try to find any WiFi device
        WIFI_DEVICE=$(adb devices | grep ":5555" | grep "device$" | awk '{print $1}' | head -1)
        if [ -n "$WIFI_DEVICE" ]; then
            ADB_TARGET="-s $WIFI_DEVICE"
            echo "Using WiFi device: $WIFI_DEVICE"
        else
            echo "Error: No WiFi device found."
            echo "Run ./deploy.sh --setup-wifi to configure."
            exit 1
        fi
    fi
elif [ "$MODE" = "emulator" ]; then
    echo "Using emulator mode..."
    # Check for running emulator
    EMULATOR_DEVICE=$(adb devices | grep -E "emulator-|localhost:" | grep "device$" | awk '{print $1}' | head -1)
    
    if [ -z "$EMULATOR_DEVICE" ]; then
        echo "No emulator running. Starting emulator..."
        
        # List available emulators
        EMULATORS=$(emulator -list-avds 2>/dev/null | head -1)
        
        if [ -z "$EMULATORS" ]; then
            echo "Error: No emulators found. Please create an AVD first."
            echo "You can create one using Android Studio or:"
            echo "  avdmanager create avd -n Pixel_API_34 -k 'system-images;android-34;google_apis;x86_64'"
            exit 1
        fi
        
        echo "Starting emulator: $EMULATORS"
        emulator -avd "$EMULATORS" -no-snapshot-load &
        
        echo "Waiting for emulator to boot..."
        adb wait-for-device
        
        # Wait for boot to complete
        while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
            echo "Waiting for boot to complete..."
            sleep 2
        done
        
        echo "Emulator is ready!"
        EMULATOR_DEVICE=$(adb devices | grep -E "emulator-|localhost:" | grep "device$" | awk '{print $1}' | head -1)
    fi
    
    if [ -n "$EMULATOR_DEVICE" ]; then
        ADB_TARGET="-s $EMULATOR_DEVICE"
        echo "Using emulator: $EMULATOR_DEVICE"
    fi
else
    # No config - use default behavior (any connected device)
    echo "No deploy config found. Using any connected device..."
    echo "Tip: Run ./deploy.sh --wifi or ./deploy.sh --emulator to set a mode."
    
    DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
    
    if [ "$DEVICES" -eq 0 ]; then
        echo "No devices connected. Starting emulator..."
        # List available emulators
        EMULATORS=$(emulator -list-avds 2>/dev/null | head -1)
        
        if [ -z "$EMULATORS" ]; then
            echo "Error: No emulators found. Please create an AVD first."
            echo "You can create one using Android Studio or:"
            echo "  avdmanager create avd -n Pixel_API_34 -k 'system-images;android-34;google_apis;x86_64'"
            exit 1
        fi
        
        echo "Starting emulator: $EMULATORS"
        emulator -avd "$EMULATORS" -no-snapshot-load &
        
        echo "Waiting for emulator to boot..."
        adb wait-for-device
        
        # Wait for boot to complete
        while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
            echo "Waiting for boot to complete..."
            sleep 2
        done
        
        echo "Emulator is ready!"
    fi
fi

# Build the app
echo ""
echo "Building the app..."
./build.sh

# Install the APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.match3.game"

if [ "$REINSTALL_FIRST" = true ]; then
    echo ""
    echo "Uninstalling existing app ($PACKAGE_NAME)..."
    if adb $ADB_TARGET uninstall "$PACKAGE_NAME"; then
        echo "Previous installation removed."
    else
        echo "Warning: Could not uninstall $PACKAGE_NAME (possibly not installed). Continuing..."
    fi
fi

echo ""
echo "Installing APK..."
adb $ADB_TARGET install -r "$APK_PATH"

# Launch the app
echo ""
echo "Launching app..."
adb $ADB_TARGET shell am start -n com.match3.game/.ui.MainActivity

echo ""
echo "=== App is running ==="
echo "To view logs: adb logcat -s Match3Game:*"
