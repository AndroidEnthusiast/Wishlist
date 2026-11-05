#!/usr/bin/env bash
# Installs the Android SDK on a fresh Claude Code cloud computer, so the app can be compiled and its screenshot tests
# run before a push (the computer is wiped after each session). Needs Network access "Full". Run from anywhere:
#   bash tests/android-setup.sh
set -e
SDK=/opt/android-sdk
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -sSLo /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -qo /tmp/clt.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /tmp/sdk.log 2>&1
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "Android SDK ready in $SDK"
