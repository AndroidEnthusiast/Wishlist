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
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-36" "build-tools;35.0.0" "platform-tools" > /tmp/sdk.log 2>&1
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
# Maven Central (repo.maven.apache.org) answers "429 Too Many Requests" to cloud computers. Google keeps an exact copy
# of it, so on this computer only, Gradle downloads from that copy. The GitHub build is not changed.
mkdir -p ~/.gradle/init.d
cat > ~/.gradle/init.d/maven-mirror.gradle.kts <<'KTS'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.useMirror() = all {
    if (this is MavenArtifactRepository && url.toString().startsWith("https://repo.maven.apache.org")) setUrl(mirror)
}
settingsEvaluated {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
KTS
# Robolectric (the app start and screenshot tests) downloads Android from Maven Central itself: the same copy.
touch ~/.gradle/gradle.properties
grep -q '^wl.mavenMirror=' ~/.gradle/gradle.properties || echo "wl.mavenMirror=https://maven-central.storage-download.googleapis.com/maven2" >> ~/.gradle/gradle.properties
echo "Android SDK ready in $SDK"
