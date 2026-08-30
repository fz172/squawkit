#!/usr/bin/env python3
"""Regenerate the string snapshot that `StringSnapshotTest` checks against (#658).

READ THE TEST'S KDOC BEFORE RUNNING THIS. Regenerating the snapshot is almost never the right
response to a failure — it is how the guard gets silently disarmed, and nothing downstream would
notice. Run it only for a *deliberate* wording change or a genuinely new string, and commit the
result in the same commit as the change it accounts for, so review sees both halves together.

Sentinels rather than backslash escapes: strings.xml legitimately contains literal "\\n" —
backslash then n — as Android escape text, so a backslash scheme cannot tell an escaped newline
from an authored one. The first attempt mangled all 12 multi-line strings doing exactly that.
Control characters cannot appear in a string resource, so there is nothing to collide with.
"""
import io, re, subprocess

NL = chr(1)
TAB = chr(2)

STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
OUT = "core/template/src/androidHostTest/resources/string_snapshot.tsv"

HEADER = f"""# Every user-facing string, captured BEFORE Phase 2C converted any of them (#658).
# Real newlines and tabs are stored as U+0001 and U+0002, because the format is line-based and 12
# strings are multi-line — and because strings.xml also holds literal backslash-n as Android escape
# text, which a backslash scheme cannot distinguish from an escaped newline.
# Everything else, including XML entities and trailing whitespace, is exactly as authored.
# Regenerating this file to make the test pass defeats its entire purpose.
module\tresource\tvalue
"""

files = subprocess.run(["git", "ls-files", "*strings.xml"],
                       capture_output=True, text=True).stdout.split()
rows = []
for f in files:
    module = f.split("/src/")[0]
    for m in STRING_RE.finditer(io.open(f, encoding="utf-8").read()):
        value = m.group(2).replace("\n", NL).replace("\t", TAB)
        rows.append((module, m.group(1), value))
rows.sort()

with io.open(OUT, "w", encoding="utf-8") as out:
    out.write(HEADER)
    for module, name, value in rows:
        out.write(f"{module}\t{name}\t{value}\n")

print(f"captured {len(rows)}; {sum(1 for r in rows if NL in r[2])} multi-line")
