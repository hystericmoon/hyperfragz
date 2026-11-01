#!/bin/bash
# Setup script for Claude Code cloud sessions (claude.ai/code).
# Paste this file's contents into the cloud environment's settings:
# environment menu → Edit → Setup script. It runs before each new session.
#
# Installs the Android SDK and points Gradle and Robolectric at Google's mirror
# of Maven Central, because Maven Central rate-limits the cloud network (HTTP 429)
# and builds stall in retries without it.
set -euo pipefail

export ANDROID_HOME=/opt/android-sdk
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  cd /tmp
  curl -fsSL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -q -o cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm cmdline-tools.zip
fi
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.0" "platform-tools" > /dev/null
echo "export ANDROID_HOME=$ANDROID_HOME" > /etc/profile.d/android.sh

mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-mirror.gradle.kts" <<'EOF'
// Cloud sessions: Maven Central throttles this network (HTTP 429), so read
// Maven Central artifacts from Google's mirror of it first.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.preferMirror() {
    if (none { it is MavenArtifactRepository && it.url.toString() == mirror }) {
        val repo = maven(mirror) { name = "MavenCentralMirror" } // added last…
        remove(repo)
        addFirst(repo) // …so move it to the front
    }
}
beforeSettings {
    pluginManagement.repositories.preferMirror()
    dependencyResolutionManagement.repositories.preferMirror()
}
// Robolectric downloads its Android runtime jars itself, also from Maven Central.
allprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", mirror.removeSuffix("/"))
        systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
    }
}
EOF
