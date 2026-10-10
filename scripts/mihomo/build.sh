#!/bin/bash
set -e

# Get the absolute path to the project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Try to load ANDROID_HOME from local.properties if it exists
if [ -z "$ANDROID_HOME" ] && [ -f "$PROJECT_ROOT/local.properties" ]; then
    export ANDROID_HOME=$(grep '^sdk.dir=' "$PROJECT_ROOT/local.properties" | cut -d'=' -f2)
    echo "Using ANDROID_HOME: $ANDROID_HOME"
fi

# Set JAVA_HOME to Android Studio's bundled JDK if javac is not in PATH
if ! command -v javac &> /dev/null; then
    if [ -d "$HOME/software/android/android-studio/jbr" ]; then
        export JAVA_HOME="$HOME/software/android/android-studio/jbr"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "Using JAVA_HOME: $JAVA_HOME"
    fi
fi

PATCH_DIR="$PROJECT_ROOT/scripts/mihomo/patches"
TARGET_DIR="$PROJECT_ROOT/vendor/mihomo-src/listener/sing_tun"

cleanup_submodule() {
    if [ -d "$PROJECT_ROOT/vendor/mihomo-src/.git" ] || [ -f "$PROJECT_ROOT/vendor/mihomo-src/.git" ]; then
        echo "Restoring clean state in vendor/mihomo-src submodule..."
        git -C "$PROJECT_ROOT/vendor/mihomo-src" checkout -- listener/sing_tun/server_android.go 2>/dev/null || true
        rm -f "$TARGET_DIR/custom_pm.go"
    fi
}

# Always restore vendor/mihomo-src to a clean git state when the build finishes
trap cleanup_submodule EXIT

# Apply custom Android PackageManager bridge files to vendor/mihomo-src before building
if [ -d "$PATCH_DIR" ] && [ -d "$TARGET_DIR" ]; then
    echo "Applying Android PackageManager bridge patches to vendor/mihomo-src..."
    cp "$PATCH_DIR/custom_pm.go" "$TARGET_DIR/custom_pm.go"
    cp "$PATCH_DIR/server_android.go" "$TARGET_DIR/server_android.go"
fi

echo "Starting Mihomo compilation..."
cd "$PROJECT_ROOT/mihomo-mobile"

# Ensure dependencies are up to date
echo "Tidying go modules..."
go mod tidy

# Build the AAR using gomobile
echo "Running gomobile bind..."
gomobile bind -v -target=android/arm64 -androidapi 21 -tags with_gvisor -ldflags="-s -w" -o mihomo.aar .

echo "Compilation successful. Copying output to dependencies..."
mkdir -p "$PROJECT_ROOT/core/libs"
cp mihomo.aar "$PROJECT_ROOT/core/libs/mihomo.aar"

# Copy sources jar if it was generated
if [ -f "mihomo-sources.jar" ]; then
    cp mihomo-sources.jar "$PROJECT_ROOT/core/libs/mihomo-sources.jar"
fi

echo "All done! mihomo.aar has been successfully updated in core/libs."
