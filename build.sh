#!/bin/bash

# Build script for Lia Raskraska Android app
set -e

echo "=== Building Lia Raskraska Android App ==="

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "Error: ANDROID_HOME is not set. Please set it to your Android SDK location."
        exit 1
    fi
fi

echo "Using ANDROID_HOME: $ANDROID_HOME"

cd "$(dirname "$0")"

# Check for gradle wrapper jar
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    curl -L -o "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
fi

chmod +x ./gradlew

echo "Building debug APK..."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=== Build Successful ==="
    echo "APK location: $APK_PATH"
else
    echo "Error: Build failed."
    exit 1
fi
