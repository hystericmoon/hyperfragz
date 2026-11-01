#!/bin/sh
# ui.sh tap "<label regex>" | list [n] | shot <name> | state
#   tap   : tap the first element whose text/content-desc starts with the regex
#   list  : texts and content-descs on screen (uiautomator)
#   shot  : screenshot to tools/out/<name>.png (resized to 1000px)
#   state : the hidden search page's query/filters via VitrineBridge (needs fwd + DevTools)
. "$(dirname "$0")/env.sh"
dump() { for i in 1 2 3; do $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && break; sleep 1; done; $ADB shell cat /sdcard/ui.xml | tr '>' '\n'; }
case "$1" in
  tap) xy=$(dump | grep -E "(content-desc|text)=\"$2" | grep -o 'bounds="[^"]*"' | head -1 | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
       [ -z "$xy" ] && { echo "NOT FOUND: $2"; exit 1; }; $ADB shell input tap $xy; echo "tapped $2 at $xy";;
  list) dump | grep -oE '(text|content-desc)="[^"]+"' | sort -u | grep -v 'Web View' | head -${2:-80};;
  shot) $ADB exec-out screencap -p > "$OUT/$2.png"; sips -Z 1000 "$OUT/$2.png" >/dev/null 2>&1; echo "$OUT/$2.png";;
  state) sh "$TOOLS/fwd.sh"
         echo '(()=>{const s=JSON.parse(VitrineBridge.readFacets());return JSON.stringify({url:location.href,total:s.total,active:s.panels.flatMap(p=>p.type==="range"?[p.title+":"+JSON.stringify(p.range)]:p.items.filter((it,i)=>it.checked&&!(p.type==="radio"&&i===0)).map(it=>p.title+":"+it.label))})})()' > "$OUT/state.js"
         python3 -I "$TOOLS/cdp.py" "$OUT/state.js";;
esac
