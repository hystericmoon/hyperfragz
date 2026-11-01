#!/bin/sh
# timing.sh N url... : open each perfume N times; print when each detail section
# first reached the screen (seconds). Debug builds log "detail +Nms" (SiteController.applyDetail).
. "$(dirname "$0")/env.sh"
N=$1; shift
for u in "$@"; do for i in $(seq $N); do
  $ADB logcat -c; sh "$TOOLS/open.sh" "$u"; sleep 16
  $ADB logcat -d -s Vitrine:D | grep "detail +" | python3 -c '
import sys,re
first={}
for l in sys.stdin:
  m=re.search(r"\+(\d+)ms complete=(\w+) (\S*)",l)
  if not m: continue
  t=int(m.group(1))
  for s in m.group(3).split(","): first.setdefault(s,t)
  if m.group(2)=="true": first.setdefault("COMPLETE",t)
print(" ".join(f"{k}={v/1000:.1f}" for k,v in first.items()))'
  $ADB shell input keyevent 4; sleep 2
done; done
