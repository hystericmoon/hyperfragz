#!/bin/sh
# cpu.sh : CPU% and memory of the app and its WebView renderer (4 samples, 5s apart).
# Idle on a perfume page should be ~1-2% each (1.1.2+).
. "$(dirname "$0")/env.sh"
$ADB shell top -b -n 4 -d 5 -o PID,%CPU,RES,NAME | grep -E "hyperfragz|sandboxed" | grep -v " 0.0 "
