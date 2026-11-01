# Sourced by the other tools. Override ANDROID_SERIAL / ADB / PKG as needed.
ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
PKG="${PKG:-com.hosni.hyperfragz.debug}"   # debug build; release is com.hosni.hyperfragz
TOOLS="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$TOOLS/out}"; mkdir -p "$OUT"
export ANDROID_SERIAL
