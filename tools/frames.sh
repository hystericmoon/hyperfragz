#!/bin/sh
# frames.sh [seconds] : frames drawn while idle. Should be 0 when nothing moves;
# ~120/s means something (often the hidden page) is forcing redraws.
. "$(dirname "$0")/env.sh"
$ADB shell dumpsys gfxinfo $PKG reset >/dev/null; sleep ${1:-5}
$ADB shell dumpsys gfxinfo $PKG | grep "Total frames"
