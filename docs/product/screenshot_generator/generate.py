#!/usr/bin/env python3
"""
Generates SquawkIt's Play Store screenshot set from raw device captures.

See README.md in this directory for the full explanation and how to add a
new screen. Quick usage:

    python3 docs/product/screenshot_generator/generate.py

Reads:
  - Raw screenshots from docs/product/screenshots/*.png
  - Brand fonts directly from core/ui/theme's composeResources (no copies
    kept here — this script always uses whatever's actually shipping)

Writes:
  - docs/product/store_assets/play_store_screenshot_<NN>_<name>.png (1080x2364)
  - docs/product/screenshot_generator/_render_<name>.html (intermediate, gitignored)
"""

import base64
import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))

SCREENSHOTS_DIR = os.path.join(REPO_ROOT, "docs", "product", "screenshots")
OUT_DIR = os.path.join(REPO_ROOT, "docs", "product", "store_assets")
FONT_DIR = os.path.join(
    REPO_ROOT, "core", "ui", "theme", "src", "commonMain", "composeResources", "font"
)

CHROME = os.environ.get(
    "CHROME_BIN", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
)

SG_BOLD_FONT = os.path.join(FONT_DIR, "space_grotesk_bold.ttf")
JBM_MEDIUM_FONT = os.path.join(FONT_DIR, "jetbrains_mono_medium.ttf")

# ---------------------------------------------------------------------------
# One entry per screenshot. "num" controls submission order in the listing.
# "src" is the filename (without .png) under docs/product/screenshots/.
# "bubble" fields were removed deliberately: never put per-user data (counts,
# names, tail numbers, dates) in the headline or on the canvas — every user's
# app state differs. Describe the *feature*, not today's content.
# object_position tunes which part of the tall screenshot shows through the
# phone frame (most captures look best at "50% 0%", i.e. anchored to the top).
# ---------------------------------------------------------------------------
SCREENS = [
    {
        "num": "01",
        "src": "airplane_overview",
        "out": "overview",
        "feature_label": "Fleet Overview",
        "l1": "Your Fleet,",
        "l2": "At a Glance.",
        "object_position": "50% 6%",
    },
    {
        "num": "02",
        "src": "maintenance_tasks",
        "out": "maintenance_tasks",
        "feature_label": "Maintenance Tasks",
        "l1": "Never Miss",
        "l2": "A Deadline.",
        "object_position": "50% 4%",
    },
    {
        "num": "03",
        "src": "squawks",
        "out": "squawks",
        "feature_label": "Squawks",
        "l1": "Every Squawk,",
        "l2": "Tracked to Closed.",
        "object_position": "50% 0%",
    },
    {
        "num": "04",
        "src": "logs",
        "out": "logs",
        "feature_label": "Maintenance Logs",
        "l1": "Every Entry,",
        "l2": "Fully Documented.",
        "object_position": "50% 0%",
    },
    {
        "num": "05",
        "src": "work_logs",
        "out": "work_logs",
        "feature_label": "Work Logs",
        "l1": "Every Task,",
        "l2": "On the Record.",
        "object_position": "50% 0%",
    },
    {
        "num": "06",
        "src": "export_data",
        "out": "export",
        "feature_label": "Export Logs",
        "l1": "Your Logbook,",
        "l2": "Ready to Share.",
        "object_position": "50% 0%",
    },
]


def b64_file(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("ascii")


def main():
    if not os.path.isfile(CHROME):
        sys.exit(
            f"Chrome not found at {CHROME!r}. Set CHROME_BIN to your Chrome/Chromium "
            "binary path and retry."
        )

    with open(os.path.join(SCRIPT_DIR, "export_template.html"), encoding="utf-8") as f:
        template = f.read()

    sg_bold = b64_file(SG_BOLD_FONT)
    jbm_medium = b64_file(JBM_MEDIUM_FONT)

    os.makedirs(OUT_DIR, exist_ok=True)

    for s in SCREENS:
        screenshot_path = os.path.join(SCREENSHOTS_DIR, f"{s['src']}.png")
        if not os.path.isfile(screenshot_path):
            sys.exit(f"Missing screenshot: {screenshot_path}")
        screenshot_b64 = b64_file(screenshot_path)

        html = template
        html = html.replace("__SG_BOLD__", sg_bold)
        html = html.replace("__JBM_MEDIUM__", jbm_medium)
        html = html.replace("__SCREENSHOT_B64__", screenshot_b64)
        html = html.replace("__HEADLINE_L1__", s["l1"])
        html = html.replace("__HEADLINE_L2__", s["l2"])
        html = html.replace("__FEATURE_LABEL__", s["feature_label"])
        html = html.replace("__OBJECT_POSITION__", s["object_position"])

        html_path = os.path.join(SCRIPT_DIR, f"_render_{s['out']}.html")
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html)

        out_path = os.path.join(
            OUT_DIR, f"play_store_screenshot_{s['num']}_{s['out']}.png"
        )
        subprocess.run(
            [
                CHROME,
                "--headless",
                "--disable-gpu",
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--screenshot=" + out_path,
                "--window-size=1080,2364",
                "file://" + html_path,
            ],
            check=True,
            capture_output=True,
        )
        os.remove(html_path)
        print("wrote", os.path.relpath(out_path, REPO_ROOT))


if __name__ == "__main__":
    main()
