#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: install_watch_release.sh [--device SERIAL] [--skip-install]

Options:
  --device SERIAL    Explicit ADB serial for the target watch. Defaults to the first "device" entry from `adb devices` or $ANDROID_SERIAL if set.
  --skip-install     Build, align, and sign but do not push to a device.
  -h, --help         Show this help message.

Environment overrides:
  SDK_DIR                  Android SDK location; falls back to local.properties, ANDROID_HOME, or ANDROID_SDK_ROOT.
  BUILD_TOOLS_VERSION      Build-tools version (e.g. 36.1.0). Defaults to the newest available.
  KEYSTORE_PATH            Path to keystore. Defaults to $HOME/.android/debug.keystore.
  KEYSTORE_ALIAS           Keystore alias. Defaults to androiddebugkey.
  KEYSTORE_PASSWORD        Keystore password. Defaults to android.
  KEY_PASS                 Key password. Defaults to KEYSTORE_PASSWORD.
  GRADLEW                  Gradle wrapper command. Defaults to ./gradlew.
USAGE
}

log() {
  printf '\n==> %s\n' "$*"
}

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

DEVICE_ID=${ANDROID_SERIAL:-}
INSTALL=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      [[ $# -ge 2 ]] || { echo "Missing value for --device" >&2; exit 1; }
      DEVICE_ID="$2"
      shift 2
      ;;
    --skip-install)
      INSTALL=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

GRADLEW=${GRADLEW:-"./gradlew"}
if [[ ! -x "$GRADLEW" ]]; then
  echo "Gradle wrapper not found at $GRADLEW" >&2
  exit 1
fi

log "Building release APK"
"$GRADLEW" :app:assembleRelease -x lintVitalRelease

APK_DIR="$PROJECT_DIR/app/build/outputs/apk/release"
UNSIGNED_APK="$APK_DIR/app-release-unsigned.apk"
ALIGNED_APK="$APK_DIR/app-release-aligned.apk"
SIGNED_APK="$APK_DIR/app-release-signed.apk"

[[ -f "$UNSIGNED_APK" ]] || { echo "Release APK not found: $UNSIGNED_APK" >&2; exit 1; }

# Resolve Android SDK path
SDK_DIR=${SDK_DIR:-}
if [[ -z "$SDK_DIR" ]]; then
  if [[ -f "$PROJECT_DIR/local.properties" ]]; then
    SDK_DIR=$(awk -F= '/^sdk.dir=/{print $2}' "$PROJECT_DIR/local.properties" | sed 's/\\r$//')
  fi
fi
if [[ -z "$SDK_DIR" ]]; then
  SDK_DIR=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Android SDK directory not found. Set SDK_DIR, ANDROID_HOME, or ANDROID_SDK_ROOT." >&2
  exit 1
fi

# Determine build-tools
BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION:-}
if [[ -z "$BUILD_TOOLS_VERSION" ]]; then
  BUILD_TOOLS_VERSION=$(ls "$SDK_DIR/build-tools" | sort -V | tail -n1)
fi
BUILD_TOOLS="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

[[ -x "$ZIPALIGN" ]] || { echo "zipalign not found at $ZIPALIGN" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "apksigner not found at $APKSIGNER" >&2; exit 1; }

log "Aligning APK"
"$ZIPALIGN" -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"

KEYSTORE_PATH=${KEYSTORE_PATH:-"$HOME/.android/debug.keystore"}
KEYSTORE_ALIAS=${KEYSTORE_ALIAS:-androiddebugkey}
KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-android}
KEY_PASS=${KEY_PASS:-$KEYSTORE_PASSWORD}

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Keystore not found at $KEYSTORE_PATH" >&2
  exit 1
fi

log "Signing APK"
"$APKSIGNER" sign \
  --ks "$KEYSTORE_PATH" \
  --ks-key-alias "$KEYSTORE_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEY_PASS" \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

log "Verifying signature"
"$APKSIGNER" verify "$SIGNED_APK"

if [[ "$INSTALL" -eq 0 ]]; then
  log "Skipping install (per flag). Signed APK ready at $SIGNED_APK"
  exit 0
fi

# Resolve adb path
if command -v adb >/dev/null 2>&1; then
  ADB_BIN=$(command -v adb)
else
  ADB_BIN="$SDK_DIR/platform-tools/adb"
fi

[[ -x "$ADB_BIN" ]] || { echo "adb not found. Install platform-tools or add adb to PATH." >&2; exit 1; }

if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID=$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi

if [[ -z "$DEVICE_ID" ]]; then
  echo "No connected devices in 'device' state. Use --device to target a watch." >&2
  exit 1
fi

log "Installing on $DEVICE_ID"
"$ADB_BIN" -s "$DEVICE_ID" install -r "$SIGNED_APK"

log "Done"
