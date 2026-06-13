#!/bin/bash
set -e

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

if [ "$1" = "--release" ] || [ "$1" = "-r" ]; then
    echo "Building SilentDragonXAndroid release APK..."
    bash gradlew assembleZcashmainnetRelease

    APK_DIR="app/build/outputs/apk/zcashmainnet/release"
    UNIVERSAL_APK=$(find "$APK_DIR" -name "*-null.apk" -type f | head -1)

    if [ -z "$UNIVERSAL_APK" ]; then
        echo "ERROR: Universal APK not found in $APK_DIR"
        exit 1
    fi

    mkdir -p "$REPO_ROOT/release"
    cp "$UNIVERSAL_APK" "$REPO_ROOT/release/SilentDragonXAndroid - v1.1.2.apk"

    echo "Release APK: release/SilentDragonXAndroid - v1.1.2.apk"
else
    echo "Building SilentDragonXAndroid debug APK..."
    bash gradlew assembleZcashmainnetDebug
    echo "Debug build complete."
fi
