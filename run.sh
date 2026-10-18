#!/bin/sh
# Build, install and launch on the connected device/emulator.
set -e
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
./gradlew -q :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -S -n com.hosni.hyperfragz.debug/com.hosni.hyperfragz.MainActivity
