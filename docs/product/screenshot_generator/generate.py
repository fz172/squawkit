#!/usr/bin/env python3
"""
Generates SquawkIt's store listing images for Google Play and the App Store.

    python3 docs/product/screenshot_generator/generate.py            # everything that has captures
    python3 docs/product/screenshot_generator/generate.py --check    # list missing captures, render nothing
    python3 docs/product/screenshot_generator/generate.py play_phone appstore_iphone

Reads:
  - Raw captures from docs/product/screenshots/<target folder>/<screen>.png
  - Brand fonts straight from core/ui/theme's composeResources
  - The 1024px app icon from iosApp's asset catalog (feature graphic only)

Writes (all under docs/product/store_assets/):
  - play/phone/<NN>_<screen>.png             1080x2364
  - play/tablet/<NN>_<screen>.png            2560x1600 (or 1600x2560 for a portrait capture)
  - play/feature_graphic.png                 1024x500
  - appstore/iphone_6_9/<NN>_<screen>.png    1320x2868
  - appstore/ipad_13/<NN>_<screen>.png       2752x2064 (or 2064x2752 for a portrait capture)

See README.md for the folder contract and the no-per-user-data rule.
"""

import argparse
import base64
import os
import struct
import subprocess
import sys
import tempfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
PRODUCT_DIR = os.path.join(REPO_ROOT, "docs", "product")
SCREENSHOTS_DIR = os.path.join(PRODUCT_DIR, "screenshots")
OUT_DIR = os.path.join(PRODUCT_DIR, "store_assets")
TEMPLATE_DIR = os.path.join(SCRIPT_DIR, "templates")
FONT_DIR = os.path.join(
    REPO_ROOT, "core", "ui", "theme", "src", "commonMain", "composeResources", "font"
)
APP_ICON = os.path.join(
    REPO_ROOT, "iosApp", "iosApp", "Assets.xcassets", "AppIcon.appiconset", "AppIcon-1024.png"
)

CHROME = os.environ.get(
    "CHROME_BIN", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
)

# ---------------------------------------------------------------------------
# Screens. One entry per listing image; "num" is the upload order. "src" is the
# capture filename (without .png) inside every target folder, so the same list
# drives phone, tablet, iPhone and iPad. A target simply skips screens whose
# capture is missing.
#
# Headline and label describe the FEATURE, never today's content: no counts,
# names, tail numbers or dates. Every user's app state differs.
#
# object_position picks which part of a capture shows through the frame when
# the frame crops it ("50% 0%" = anchored to the top).
# ---------------------------------------------------------------------------
SCREENS = [
    {
        "num": "01",
        "src": "overview",
        "feature_label": "Dashboard",
        "l1": "Airplane, Car, Home.",
        "l2": "One Record.",
        "object_position": "50% 6%",
    },
    {
        "num": "02",
        "src": "tasks",
        "feature_label": "Maintenance Tasks",
        "l1": "Never Miss",
        "l2": "A Due Date.",
        "object_position": "50% 4%",
    },
    {
        "num": "03",
        "src": "squawks",
        "feature_label": "Squawks & Issues",
        "l1": "Spot It, Log It,",
        "l2": "Track It to Closed.",
        "object_position": "50% 0%",
    },
    {
        "num": "04",
        "src": "logs",
        "feature_label": "Maintenance Logs",
        "l1": "Every Entry,",
        "l2": "Fully Documented.",
        "object_position": "50% 0%",
    },
    {
        "num": "05",
        "src": "work_logs",
        "feature_label": "Work Logs",
        "l1": "Every Job,",
        "l2": "On the Record.",
        "object_position": "50% 0%",
    },
    {
        "num": "06",
        "src": "export",
        "feature_label": "Export",
        "l1": "Your Records,",
        "l2": "Ready to Share.",
        "object_position": "50% 0%",
    },
    {
        "num": "07",
        "src": "sharing",
        "feature_label": "Sharing",
        "l1": "Your Mechanic,",
        "l2": "On the Same Page.",
        "object_position": "50% 0%",
    },
]

# ---------------------------------------------------------------------------
# Targets. "canvas" gives the output size per capture orientation; the
# orientation is read from the capture itself, so landscape tablet captures
# render landscape canvases and portrait ones render portrait canvases.
# ---------------------------------------------------------------------------
TARGETS = {
    "play_phone": {
        "src_dir": "phone",
        "out_dir": os.path.join("play", "phone"),
        "device": "phone",
        "canvas": {"portrait": (1080, 2364), "landscape": (2364, 1080)},
        "store": "Google Play · Phone",
    },
    "play_tablet": {
        "src_dir": "tablet",
        "out_dir": os.path.join("play", "tablet"),
        "device": "tablet",
        "canvas": {"portrait": (1600, 2560), "landscape": (2560, 1600)},
        "store": "Google Play · 7-inch and 10-inch tablet (upload the same set to both)",
    },
    "appstore_iphone": {
        "src_dir": "iphone",
        "out_dir": os.path.join("appstore", "iphone_6_9"),
        "device": "phone",
        "canvas": {"portrait": (1320, 2868), "landscape": (2868, 1320)},
        "store": "App Store · iPhone 6.9-inch",
    },
    "appstore_ipad": {
        "src_dir": "ipad",
        "out_dir": os.path.join("appstore", "ipad_13"),
        "device": "tablet",
        "canvas": {"portrait": (2064, 2752), "landscape": (2752, 2064)},
        "store": "App Store · iPad 13-inch",
    },
}

FEATURE_GRAPHIC = {
    "out": os.path.join("play", "feature_graphic.png"),
    "size": (1024, 500),
    "l1": "Every thing you maintain.",
    "l2": "One record.",
    "phone_src": os.path.join("phone", "overview.png"),
}


def b64_file(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("ascii")


def png_size(path):
    with open(path, "rb") as f:
        header = f.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        sys.exit(f"Not a PNG: {path}")
    return struct.unpack(">II", header[16:24])


def load_template(name):
    with open(os.path.join(TEMPLATE_DIR, name), encoding="utf-8") as f:
        return f.read()


def fill(template, values):
    for key, value in values.items():
        template = template.replace(f"__{key}__", str(value))
    return template


def render(html, size, out_path):
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as f:
        f.write(html)
        html_path = f.name
    try:
        subprocess.run(
            [
                CHROME,
                "--headless",
                "--disable-gpu",
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--screenshot=" + out_path,
                f"--window-size={size[0]},{size[1]}",
                "file://" + html_path,
            ],
            check=True,
            capture_output=True,
        )
    finally:
        os.remove(html_path)
    print("wrote", os.path.relpath(out_path, REPO_ROOT))


def captures_for(target):
    src_dir = os.path.join(SCREENSHOTS_DIR, target["src_dir"])
    present, missing = [], []
    for s in SCREENS:
        path = os.path.join(src_dir, f"{s['src']}.png")
        (present if os.path.isfile(path) else missing).append((s, path))
    return present, missing


def render_target(name, target, fonts):
    present, missing = captures_for(target)
    if not present:
        print(f"[{name}] no captures in docs/product/screenshots/{target['src_dir']}/ — skipped")
        return
    for s, _ in missing:
        print(f"[{name}] no capture for '{s['src']}' — skipped")

    for s, capture in present:
        w, h = png_size(capture)
        orientation = "portrait" if h >= w else "landscape"
        size = target["canvas"][orientation]
        template = load_template(f"{orientation}.html")
        html = fill(
            template,
            {
                **fonts,
                "CANVAS_W": size[0],
                "CANVAS_H": size[1],
                "DEVICE": target["device"],
                "DEVICE_ASPECT": f"{w} / {h}",
                "SCREENSHOT_B64": b64_file(capture),
                "HEADLINE_L1": s["l1"],
                "HEADLINE_L2": s["l2"],
                "FEATURE_LABEL": s["feature_label"],
                "OBJECT_POSITION": s["object_position"],
            },
        )
        out_path = os.path.join(OUT_DIR, target["out_dir"], f"{s['num']}_{s['src']}.png")
        render(html, size, out_path)


def render_feature_graphic(fonts):
    if not os.path.isfile(APP_ICON):
        sys.exit(f"Missing app icon: {APP_ICON}")
    phone = os.path.join(SCREENSHOTS_DIR, FEATURE_GRAPHIC["phone_src"])
    has_phone = os.path.isfile(phone)
    if not has_phone:
        print("[feature_graphic] no phone/overview.png capture — rendering without a device")
    aspect = "%d / %d" % png_size(phone) if has_phone else "1080 / 2364"
    html = fill(
        load_template("feature_graphic.html"),
        {
            **fonts,
            "ICON_B64": b64_file(APP_ICON),
            "HEADLINE_L1": FEATURE_GRAPHIC["l1"],
            "HEADLINE_L2": FEATURE_GRAPHIC["l2"],
            "HAS_PHONE": "" if has_phone else "hidden",
            "DEVICE_ASPECT": aspect,
            "SCREENSHOT_B64": b64_file(phone) if has_phone else "",
        },
    )
    render(html, FEATURE_GRAPHIC["size"], os.path.join(OUT_DIR, FEATURE_GRAPHIC["out"]))


def check():
    ok = True
    for name, target in TARGETS.items():
        present, missing = captures_for(target)
        print(f"{name}  ({target['store']})")
        print(f"  folder: docs/product/screenshots/{target['src_dir']}/")
        for s, _ in present:
            print(f"    ✓ {s['src']}.png")
        for s, _ in missing:
            print(f"    ✗ {s['src']}.png")
        ok = ok and not missing
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="*", choices=[*TARGETS, "feature_graphic"], metavar="target")
    parser.add_argument("--check", action="store_true", help="report which captures are present, render nothing")
    args = parser.parse_args()

    if args.check:
        sys.exit(0 if check() else 1)

    if not os.path.isfile(CHROME):
        sys.exit(
            f"Chrome not found at {CHROME!r}. Set CHROME_BIN to your Chrome/Chromium "
            "binary path and retry."
        )

    fonts = {
        "SG_BOLD": b64_file(os.path.join(FONT_DIR, "space_grotesk_bold.ttf")),
        "JBM_MEDIUM": b64_file(os.path.join(FONT_DIR, "jetbrains_mono_medium.ttf")),
    }

    wanted = args.targets or [*TARGETS, "feature_graphic"]
    for name in wanted:
        if name == "feature_graphic":
            render_feature_graphic(fonts)
        else:
            render_target(name, TARGETS[name], fonts)


if __name__ == "__main__":
    main()
