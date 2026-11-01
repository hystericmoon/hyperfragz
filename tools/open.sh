#!/bin/sh
# open.sh <fragrantica url> : open a perfume/search link in the debug app.
. "$(dirname "$0")/env.sh"
$ADB shell am start -n $PKG/com.hosni.hyperfragz.MainActivity --es open "$1" >/dev/null
