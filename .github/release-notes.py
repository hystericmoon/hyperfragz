"""Prints the "## Release notes" section of a pull request description (release.yml).

The section runs from a "## Release notes" heading to the next level-1 or level-2
heading, or the "Generated with Claude Code" footer. Prints nothing if there is none.
"""
import re
import sys

lines = open(sys.argv[1], encoding="utf-8").read().replace("\r\n", "\n").split("\n")
out, inside = [], False
for line in lines:
    if re.match(r"^##\s+Release notes\s*$", line, re.I):
        inside = True
        continue
    if inside and (re.match(r"^#{1,2}\s", line) or line.startswith("🤖 Generated with")):
        break
    if inside:
        out.append(line)
notes = "\n".join(out).strip()
if notes:
    print(notes)
