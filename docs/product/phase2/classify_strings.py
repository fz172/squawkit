#!/usr/bin/env python3
"""Classify every string in the corpus into PRD §10's three buckets.

Produces a reviewable TSV. The classifier is a *first pass* — it flags candidates by matching
domain vocabulary, and every hit still needs a human read, because the interesting cases are the
ones where the word is present but the string is not actually domain-specific ("Aircraft Data" is
substitutable; "Airworthiness Directive" is not; "AOG squawks must be resolved before flight" is
neither, because "flight" has no lexicon slot).
"""
import io, re, subprocess, sys
from collections import Counter

# Words that name the domain. Split by which lexicon slot they belong to, because that is what
# decides the bucket: a word with a slot can be substituted, a word without one cannot.
SLOT_WORDS = {
    "thing": ["aircraft", "airplane", "plane", "tail number", "tail_number"],
    "squawk": ["squawk", "discrepancy", "discrepancies"],
    "task": ["inspection", "maintenance task"],
    "log": ["logbook", "work log"],
    "component": ["airframe", "component"],
    "technician": ["technician", "mechanic", "a&p", "ia "],
    "down_status": ["aog", "aircraft on ground", "grounded"],
    "ready_status": ["airworthy"],
    "compliance_mandatory": ["airworthiness directive", "airworthiness"],
    "compliance_advisory": ["service bulletin"],
    "authority_label": ["faa"],
    "collection_label": ["fleet"],
}

# Aviation vocabulary with NO lexicon slot. Present in a string means the string cannot be made
# domain-neutral by substitution — it has to be replaced wholesale or stay put.
NO_SLOT_WORDS = [
    "flight", "flying", "pilot", "takeoff", "hangar", "tach", "hobbs", "engine hour",
    "propeller", "prop time", "n-number", "registration number", "cessna", "kias",
    "annual", "100-hour", "certificate", "cert ", "endorsement", "avionics",
]

STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)

# The product is called SquawkIt. Its name contains a lexicon word and must never be substituted —
# a boat owner's app is still called SquawkIt. Matched case-sensitively before lowercasing.
BRAND = "SquawkIt"

# Resource names that are brand or developer surface, never user-facing domain copy.
NEVER_SUBSTITUTE_PREFIXES = ("app_name", "stress_test", "developer_options", "devoptions")


def _matches(word, hay):
    """Whole-word match, with two corrections that each silently changed the bucket counts.

    Substring matching put every 'attachment' in the aviation bucket, because 'tach' is inside
    'attach' — 'tach time' is a meter, attachments are files. So: word boundaries.

    But `\b` treats `_` as a word character, so `\baircraft\b` never matched the resource NAME
    `aircraft_shared_badge`; and it is exact, so `\bsquawk\b` missed the value "Squawks". The
    haystack therefore has underscores flattened to spaces, and an optional plural 's' is allowed.
    """
    if not word.replace(" ", "").isalnum():          # "a&p", "100-hour"
        return word in hay
    return re.search(rf"\b{re.escape(word)}s?\b", hay) is not None


def classify(name, value):
    """Return (bucket, slots, reason)."""
    if name.startswith(NEVER_SUBSTITUTE_PREFIXES):
        return "NEUTRAL", [], "brand or developer surface"

    # Strip the product name before looking for domain words, or every string mentioning SquawkIt
    # reads as a squawk string.
    hay = f"{name} {value.replace(BRAND, '')}".lower().replace("_", " ")

    slots = sorted({slot for slot, words in SLOT_WORDS.items()
                    if any(_matches(w, hay) for w in words)})
    no_slot = [w for w in NO_SLOT_WORDS if _matches(w, hay)]

    if not slots and not no_slot:
        return "NEUTRAL", [], "no domain vocabulary"
    if no_slot:
        # Aviation words with nowhere to go. Even if a substitutable noun is also present, the
        # string cannot be fully neutralised — so it needs a whole-string decision.
        return "STRUCTURAL", slots, f"no lexicon slot for: {', '.join(no_slot[:3])}"
    return "SUBSTITUTABLE", slots, f"slots: {', '.join(slots)}"


def main():
    files = subprocess.run(["git", "ls-files", "*strings.xml"],
                           capture_output=True, text=True).stdout.split()
    rows, counts = [], Counter()
    for f in files:
        module = f.split("/src/")[0]
        src = io.open(f, encoding="utf-8").read()
        for m in STRING_RE.finditer(src):
            name, value = m.group(1), " ".join(m.group(2).split())
            bucket, slots, reason = classify(name, value)
            counts[bucket] += 1
            rows.append((bucket, module, name, ";".join(slots), reason, value))

    rows.sort(key=lambda r: (r[0], r[1], r[2]))
    out = io.open(sys.argv[1], "w", encoding="utf-8")
    out.write("bucket\tmodule\tresource\tslots\treason\tvalue\n")
    for r in rows:
        out.write("\t".join(r) + "\n")
    out.close()

    total = sum(counts.values())
    print(f"{total} strings across {len(files)} files\n")
    for b in ("NEUTRAL", "SUBSTITUTABLE", "STRUCTURAL"):
        print(f"  {counts[b]:4}  {b}   ({100*counts[b]//total}%)")
    print(f"\n  {counts['SUBSTITUTABLE'] + counts['STRUCTURAL']:4}  name the domain")


if __name__ == "__main__":
    main()
