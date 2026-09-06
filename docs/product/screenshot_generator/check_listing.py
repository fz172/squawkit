#!/usr/bin/env python3
"""Checks store listing copy against each console's character limit.

    python3 docs/product/screenshot_generator/check_listing.py

Reads the fenced code blocks and the name row from docs/product/store_listing.md
plus play_store_description.txt, and fails if anything is over its limit.
"""

import os
import re
import sys

PRODUCT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# Field heading in store_listing.md -> limit. Headings are matched on their
# bold label prefix, so the "(n)" in the heading is documentation only.
LIMITS = {
    "Short description": 80,
    "Subtitle": 30,
    "Promotional text": 170,
    "Keywords": 100,
    "Google Play release notes": 500,
    "App Store “What’s New”": 4000,
}
NAME_LIMIT = 30
DESCRIPTION_LIMIT = 4000


def main():
    with open(os.path.join(PRODUCT_DIR, "store_listing.md"), encoding="utf-8") as f:
        md = f.read()

    failures = []

    name = re.search(r"App name \(\d+\) \| `([^`]+)`", md).group(1)
    report("App name", name, NAME_LIMIT, failures)

    for label, limit in LIMITS.items():
        m = re.search(r"\*\*" + re.escape(label) + r".*?```\n(.*?)\n```", md, re.S)
        if not m:
            failures.append(f"{label}: not found in store_listing.md")
            continue
        report(label, m.group(1).strip(), limit, failures)

    with open(os.path.join(PRODUCT_DIR, "play_store_description.txt"), encoding="utf-8") as f:
        report("Full description", f.read().rstrip("\n"), DESCRIPTION_LIMIT, failures)

    if failures:
        sys.exit("\n".join(failures))


def report(label, text, limit, failures):
    n = len(text)
    status = "ok" if n <= limit else "OVER"
    print(f"{label:26} {n:>5}/{limit:<5} {status}")
    if n > limit:
        failures.append(f"{label} is {n - limit} over its {limit}-character limit")


if __name__ == "__main__":
    main()
