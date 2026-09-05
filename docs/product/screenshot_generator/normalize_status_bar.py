#!/usr/bin/env python3
"""
Gives a capture the same Android status bar as the rest of the set.

    python3 docs/product/screenshot_generator/normalize_status_bar.py \\
        docs/product/screenshots/phone/log_detail.png docs/product/screenshots/phone/logs.png

The first file is rewritten in place. Its top strip (the status bar) is replaced by the donor's
status-bar glyphs on a background sampled from the first file just below the bar, so a capture
taken outside demo mode, or with a dimming scrim behind a sheet, matches its neighbours.

Requires ffmpeg. Bar height is ANDROID_STATUS_BAR_PX from generate.py, scaled by capture width.
"""

import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate import ANDROID_STATUS_BAR_PX, png_size  # noqa: E402


def pixel(path, x, y):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-vf", f"crop=1:1:{x}:{y}",
         "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
        check=True, capture_output=True,
    ).stdout
    return "0x%02x%02x%02x" % tuple(raw[:3])


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    target, donor = sys.argv[1:]
    w, h = png_size(target)
    if png_size(donor)[0] != w:
        sys.exit("Donor must have the same width as the target")
    bar = round(ANDROID_STATUS_BAR_PX * w / 1080)

    # The target's own background just under the bar, in the left margin where nothing is drawn.
    background = pixel(target, 8, bar + 8)
    # The donor's bar background, keyed out so only its glyphs survive.
    donor_bg = pixel(donor, 8, 8)

    tmp = target + ".tmp.png"
    subprocess.run(
        [
            "ffmpeg", "-v", "error", "-y",
            "-i", target, "-i", donor,
            "-filter_complex",
            f"color=c={background}:s={w}x{bar}:d=1[bg];"
            f"[1:v]crop={w}:{bar}:0:0,colorkey={donor_bg}:0.02:0[glyphs];"
            f"[bg][glyphs]overlay=shortest=1[strip];"
            f"[0:v][strip]overlay=0:0:shortest=1,format=rgb24",
            "-frames:v", "1", "-update", "1", tmp,
        ],
        check=True,
    )
    os.replace(tmp, target)
    print(f"rewrote {target}: bar {bar}px, background {background}, glyphs from {os.path.basename(donor)}")


if __name__ == "__main__":
    main()
