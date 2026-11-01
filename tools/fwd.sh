#!/bin/sh
# Forward the hidden WebView's DevTools socket to localhost:9222 (debug builds only).
. "$(dirname "$0")/env.sh"
S=$($ADB shell cat /proc/net/unix | grep -o 'webview_devtools_remote_[0-9]*' | tail -1)
$ADB forward --remove-all; $ADB forward tcp:9222 localabstract:$S >/dev/null
