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
  - play/tablet/<NN>_<screen>.png            2560x1600 (or 1600x2560 for a portrait capture;
                                             derived from ipad/ captures when tablet/ is empty)
  - play/feature_graphic.png                 1024x500
  - appstore/iphone_6_9/<NN>_<screen>.png    1320x2868
  - appstore/iphone_6_5/<NN>_<screen>.png    1284x2778
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
# capture is missing. "src" may also be a list: the first capture is the front
# device and the rest stack behind it, up-left, so several dashboards show in
# one image. Only the first is required.
#
# Headline and label describe the FEATURE, never today's content: no counts,
# names, tail numbers or dates. Every user's app state differs. Avoid aviation
# jargon and words that imply something is broken; the app covers cars, boats
# and homes too.
#
# object_position picks which part of a capture shows through the frame when
# the frame crops it ("50% 0%" = anchored to the top). headline_scale (default 1)
# shrinks the headline for a line that would otherwise wrap.
# ---------------------------------------------------------------------------
SCREENS = [
    {
        "num": "01",
        "src": ["overview_1", "overview_2", "overview_3"],
        "feature_label": "Dashboard",
        "l1": "Airplane, Car, Home,",
        "l2": "And More. One Record.",
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
        "feature_label": "Observations",
        "l1": "Notice It, Note It,",
        "l2": "See It Through.",
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
        "src": "log_detail",
        "feature_label": "Attachments",
        "l1": "Photos and Receipts,",
        "l2": "Kept With the Entry.",
        "object_position": "50% 0%",
    },
    {
        "num": "06",
        "src": "export",
        "feature_label": "Export",
        "l1": "Your Records,",
        "l2": "Ready to Archive.",
        "object_position": "50% 0%",
    },
    {
        "num": "07",
        "src": "sharing",
        "feature_label": "Sharing",
        "l1": "Collaboration,",
        "l2": "Always on the Same Page.",
        "headline_scale": 0.84,
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
        # With no tablet/ captures, reuse the iPad captures with an Android status bar painted
        # over the iPadOS one. The app lays out identically on both.
        "derive_from": "ipad",
        "derived_bar": "android_tablet",
    },
    "appstore_iphone": {
        "src_dir": "iphone",
        "out_dir": os.path.join("appstore", "iphone_6_9"),
        "device": "phone iphone",
        "canvas": {"portrait": (1320, 2868), "landscape": (2868, 1320)},
        "store": "App Store · iPhone 6.9-inch",
        # With no iphone/ captures, reuse the Android phone captures: the Android status bar is
        # cropped off and an iOS one drawn in its place, so the frame reads as an iPhone.
        "derive_from": "phone",
        "derived_bar": "iphone",
    },
    "appstore_iphone_6_5": {
        "src_dir": "iphone",
        "out_dir": os.path.join("appstore", "iphone_6_5"),
        "device": "phone iphone",
        "canvas": {"portrait": (1284, 2778), "landscape": (2778, 1284)},
        "store": "App Store · iPhone 6.5-inch (App Store Connect asks for this slot too)",
        "derive_from": "phone",
        "derived_bar": "iphone",
    },
    "appstore_ipad": {
        "src_dir": "ipad",
        "out_dir": os.path.join("appstore", "ipad_13"),
        "device": "tablet",
        "canvas": {"portrait": (2064, 2752), "landscape": (2752, 2064)},
        "store": "App Store · iPad 13-inch",
        # Simulator captures carry the real clock; repaint it as 9:41 with a full battery.
        "status_bar": "ipad",
    },
}

# Height of the Android status bar in a 1080px-wide Pixel capture (time, icons, and the padding
# under them). Scaled by the capture's width for other densities.
ANDROID_STATUS_BAR_PX = 150

FEATURE_GRAPHIC = {
    "out": os.path.join("play", "feature_graphic.png"),
    "size": (1024, 500),
    "l1": "Every thing you maintain.",
    "l2": "One record.",
    "phone_src": os.path.join("phone", "overview_1.png"),
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


def sources(screen):
    src = screen["src"]
    return list(src) if isinstance(src, (list, tuple)) else [src]


def captures_for(target):
    """Returns (present, missing): present pairs each screen with the capture
    paths that exist, front first; a screen is missing when its first capture is."""
    src_dir = os.path.join(SCREENSHOTS_DIR, target["src_dir"])
    derived = False
    if not any(f.endswith(".png") for f in os.listdir(src_dir)) and target.get("derive_from"):
        src_dir = os.path.join(SCREENSHOTS_DIR, target["derive_from"])
        derived = True
    present, missing = [], []
    for s in SCREENS:
        paths = [os.path.join(src_dir, f"{name}.png") for name in sources(s)]
        found = [p for p in paths if os.path.isfile(p)]
        if found and found[0] == paths[0]:
            present.append((s, found))
        else:
            missing.append((s, paths[0]))
    return present, missing, derived


WIFI_SVG = """<svg viewBox="0 0 16 12" class="wifi"><path d="M8 11.2a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3Zm-3.3-4.3a4.7 4.7 0 0 1 6.6 0l-1.2 1.2a3 3 0 0 0-4.2 0Zm-2.4-2.4a8 8 0 0 1 11.4 0l-1.2 1.2a6.3 6.3 0 0 0-9 0ZM0 2.1A11.3 11.3 0 0 1 16 2.1l-1.2 1.2a9.6 9.6 0 0 0-13.6 0Z"/></svg>"""
CELL_SVG = """<svg viewBox="0 0 20 12" class="cell"><rect x="0" y="8" width="3.5" height="4" rx="0.8"/><rect x="5.5" y="5.5" width="3.5" height="6.5" rx="0.8"/><rect x="11" y="3" width="3.5" height="9" rx="0.8"/><rect x="16.5" y="0" width="3.5" height="12" rx="0.8"/></svg>"""
BATTERY_SVG = """<svg viewBox="0 0 28 13" class="battery"><rect x="0.5" y="0.5" width="24" height="12" rx="3.5" fill="none" stroke-width="1"/><rect x="2" y="2" width="21" height="9" rx="2"/><path d="M26 4.5v4a2 2 0 0 0 0-4Z"/></svg>"""

# Full-width bar drawn over the strip left by cropping an Android phone capture's status bar.
IPHONE_STATUS_BAR = f"""<div class="statusbar">
  <span class="time">9:41</span>
  <span class="icons">{CELL_SVG}{WIFI_SVG}{BATTERY_SVG}</span>
</div>"""

# Two patches, each painted in the bar's own colour by the template script, covering the clock
# on the left and the battery readout on the right of a tablet capture. Nothing else is touched.
IPAD_STATUS_BAR = f"""<div class="patch clock"><span>9:41 AM</span><span class="date">Mon Jun 9</span></div>
<div class="patch status">{WIFI_SVG}{BATTERY_SVG}</div>"""
ANDROID_TABLET_STATUS_BAR = f"""<div class="patch clock android"><span>5:00</span></div>
<div class="patch status android">{WIFI_SVG}<span class="pill">100</span></div>"""

STATUS_BARS = {
    "iphone": IPHONE_STATUS_BAR,
    "ipad": IPAD_STATUS_BAR,
    "android_tablet": ANDROID_TABLET_STATUS_BAR,
}


def device_html(captures, device, bar=None):
    """One .device div per capture; index 0 is the front device.

    bar names a status-bar treatment from STATUS_BARS. "iphone" crops the capture's Android
    status bar off (margin as a fraction of the screen width, since that is what percentage
    margins resolve against) and draws an iOS bar over the strip. "ipad" and "android_tablet"
    leave the capture in place and paint patches over its clock and battery readout. The
    template script colours every bar from the capture itself.
    """
    out = []
    for i, path in enumerate(captures):
        w, _ = png_size(path)
        img = f'<img src="data:image/png;base64,{b64_file(path)}" alt="" />'
        if bar == "iphone":
            crop = ANDROID_STATUS_BAR_PX * w / 1080
            screen = (
                f'<div class="screen ios" style="--crop: {crop / w * 100:.3f}%">'
                f"{img}{STATUS_BARS[bar]}</div>"
            )
        elif bar:
            screen = f'<div class="screen">{img}{STATUS_BARS[bar]}</div>'
        else:
            screen = f'<div class="screen">{img}</div>'
        out.append(f'<div class="device {device} d{i}" style="--i: {i}">{screen}</div>')
    return "\n".join(out)


def render_target(name, target, fonts):
    present, missing, derived = captures_for(target)
    if not present:
        print(f"[{name}] no captures in docs/product/screenshots/{target['src_dir']}/ — skipped")
        return
    bar = target.get("derived_bar") if derived else target.get("status_bar")
    if derived:
        print(f"[{name}] deriving from {target['derive_from']}/ captures with a {bar} status bar")
    for s, path in missing:
        print(f"[{name}] no capture {os.path.basename(path)} — skipped")

    for s, captures in present:
        w, h = png_size(captures[0])
        orientation = "portrait" if h >= w else "landscape"
        size = target["canvas"][orientation]
        template = load_template(f"{orientation}.html")
        html = fill(
            template,
            {
                **fonts,
                "CANVAS_W": size[0],
                "CANVAS_H": size[1],
                "CANVAS_CLASS": "stack" if len(captures) > 1 else "",
                "DEVICE_ASPECT": f"{w} / {h}",
                "DEVICES": device_html(captures, target["device"], bar),
                "HEADLINE_L1": s["l1"],
                "HEADLINE_L2": s["l2"],
                "HEADLINE_SCALE": s.get("headline_scale", 1),
                "FEATURE_LABEL": s["feature_label"],
                "OBJECT_POSITION": s["object_position"],
            },
        )
        out_name = sources(s)[0].rsplit("_", 1)[0] if len(sources(s)) > 1 else sources(s)[0]
        out_path = os.path.join(OUT_DIR, target["out_dir"], f"{s['num']}_{out_name}.png")
        render(html, size, out_path)


def render_feature_graphic(fonts):
    if not os.path.isfile(APP_ICON):
        sys.exit(f"Missing app icon: {APP_ICON}")
    phone = os.path.join(SCREENSHOTS_DIR, FEATURE_GRAPHIC["phone_src"])
    has_phone = os.path.isfile(phone)
    if not has_phone:
        print("[feature_graphic] no phone/overview_1.png capture — rendering without a device")
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
        present, missing, derived = captures_for(target)
        print(f"{name}  ({target['store']})")
        folder = target["derive_from"] if derived else target["src_dir"]
        print(f"  folder: docs/product/screenshots/{folder}/" + (" (derived)" if derived else ""))
        for s, found in present:
            for name in sources(s):
                mark = "✓" if any(f.endswith(f"{name}.png") for f in found) else "·"
                print(f"    {mark} {name}.png")
        for s, _ in missing:
            for i, name in enumerate(sources(s)):
                print(f"    {'✗' if i == 0 else '·'} {name}.png")
        ok = ok and not missing
    print("✓ present   ✗ missing   · optional stack capture, not present")
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
