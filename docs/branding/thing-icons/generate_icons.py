#!/usr/bin/env python3
"""
Single source for the Thing glyphs: car, bike, boat, home, toolbox, plus the open crate the login
hero pours them into. Drawn in the brand plane's style: chunky filled silhouettes, rounded corners,
a few speed dashes on things that move.

    python3 docs/branding/thing-icons/generate_icons.py

Writes, from the shape definitions below:
  - docs/branding/thing-icons/<name>.svg            monochrome, currentColor, for UI use
  - docs/branding/thing-icons/<name>-color.svg      the app icon's cyan-to-violet gradient
  - docs/branding/thing-icons/contact-sheet.html    every glyph on navy and on white, for review
  - core/ui/src/commonMain/kotlin/.../brand/ThingGlyphPaths.kt   the same paths for Compose

Every glyph lives in a 1024x1024 box, like ic_launcher_foreground, so they mix with the plane at
one scale. Outer shapes wind clockwise and holes counter-clockwise; fills are nonzero.
"""

import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
KT_OUT = os.path.join(
    REPO, "core", "ui", "src", "commonMain", "kotlin", "dev", "fanfly", "wingslog", "core", "ui",
    "brand", "ThingGlyphPaths.kt",
)

GRADIENT = ("#00E5FF", "#651FFF")  # the app icon's plane gradient


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def rounded_polygon(points, r, reverse=False):
    """Closed path through `points` with each corner rounded by radius r (quadratic corners)."""
    pts = list(reversed(points)) if reverse else list(points)
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        d1 = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
        d2 = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
        rr = min(r, d1 / 2, d2 / 2)
        a = (p1[0] + (p0[0] - p1[0]) * rr / d1, p1[1] + (p0[1] - p1[1]) * rr / d1)
        b = (p1[0] + (p2[0] - p1[0]) * rr / d2, p1[1] + (p2[1] - p1[1]) * rr / d2)
        out.append(("M" if i == 0 else "L") + f"{f(a[0])},{f(a[1])}")
        out.append(f"Q{f(p1[0])},{f(p1[1])} {f(b[0])},{f(b[1])}")
    out.append("Z")
    return "".join(out)


def rect(x0, y0, x1, y1, r, reverse=False):
    return rounded_polygon([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], r, reverse)


def circle(cx, cy, r, reverse=False):
    s = 0 if reverse else 1
    return (
        f"M{f(cx - r)},{f(cy)}"
        f"A{f(r)},{f(r)} 0 1 {s} {f(cx + r)},{f(cy)}"
        f"A{f(r)},{f(r)} 0 1 {s} {f(cx - r)},{f(cy)}Z"
    )


def bar(x0, x1, y, h=48):
    """A horizontal speed dash with fully round ends."""
    return rect(x0, y - h / 2, x1, y + h / 2, h / 2)


def dashes(x_end, y0, gap=76, lengths=(150, 105, 60)):
    """Three motion dashes ending at x_end, longest on top, as the plane's read from bottom-left."""
    return [bar(x_end - l, x_end, y0 + i * gap) for i, l in enumerate(lengths)]


# A glyph is a list of (path, kind) where kind is "fill" or ("stroke", width).
FILL = "fill"


def stroke(w):
    return ("stroke", w)


def car():
    body = rounded_polygon(
        [(300, 655), (300, 575), (370, 545), (455, 425), (700, 425), (820, 545), (930, 575), (930, 655)],
        42,
    )
    # Cabin glass, split by the B pillar; the windscreen leans back.
    rear_glass = rounded_polygon([(470, 452), (585, 452), (585, 535), (398, 535)], 14, reverse=True)
    front_glass = rounded_polygon([(615, 452), (700, 452), (790, 535), (615, 535)], 14, reverse=True)
    wheels = []
    for cx in (440, 790):
        wheels.append(circle(cx, 665, 92, reverse=True))  # arch
        wheels.append(circle(cx, 665, 56))  # tyre
    return [(body + rear_glass + front_glass + "".join(wheels), FILL)] + [
        (d, FILL) for d in dashes(240, 520)
    ]


def bike():
    parts = []
    for cx in (380, 800):
        parts.append(circle(cx, 660, 150))
        parts.append(circle(cx, 660, 98, reverse=True))
    rings = "".join(parts)
    # Frame as thick round-capped strokes: rear wheel, bottom bracket, seat, head tube, fork.
    frame = (
        "M380,660 L590,672 L520,410 L740,410 L800,660 M590,672 L740,410 M380,660 L520,410 "
        "M520,410 L505,350 M455,345 L560,345 M740,410 L716,330 M672,320 L775,300"
    )
    return [(rings, FILL), (frame, stroke(50))]


def boat():
    hull = rounded_polygon([(300, 690), (890, 690), (800, 810), (400, 810)], 34)
    mast = rect(590, 200, 626, 690, 18)
    main = "M572,235 L572,655 L300,655 Q345,470 572,235Z"
    jib = "M646,300 L646,645 L890,645 Q810,450 646,300Z"
    return [(hull + mast + main + jib, FILL)] + [(d, FILL) for d in dashes(255, 720, gap=70, lengths=(130, 90, 50))]


def home():
    walls = rect(330, 500, 780, 830, 34)
    roof = rounded_polygon([(250, 545), (555, 240), (860, 545)], 40)
    chimney = rect(690, 300, 760, 450, 16)
    door = rect(505, 640, 605, 830, 22, reverse=True)
    window = rect(385, 585, 465, 665, 14, reverse=True)
    return [(walls + roof + chimney + door + window, FILL)]


def toolbox():
    body = rect(200, 470, 824, 800, 44)
    seam = rect(200, 566, 824, 590, 12, reverse=True)
    latch = rect(478, 528, 546, 628, 14, reverse=True)
    handle = "M400,470 L400,410 Q400,340 470,340 L554,340 Q624,340 624,410 L624,470"
    return [(body + seam + latch, FILL), (handle, stroke(52))]


def crate():
    """The open box the hero pours things into. Its outer outline (first subpath) is what morphs
    into the plane's body, so it stays one simple closed shape; the mouth is a hole."""
    box = rounded_polygon(
        [(250, 470), (140, 330), (395, 330), (445, 470), (579, 470), (629, 330), (884, 330), (774, 470), (774, 820), (250, 820)],
        30,
    )
    mouth = rect(292, 470, 732, 545, 18, reverse=True)
    return [(box + mouth, FILL)]


GLYPHS = {
    "car": car,
    "bike": bike,
    "boat": boat,
    "home": home,
    "toolbox": toolbox,
    "crate": crate,
}


def svg(name, parts, color):
    defs = ""
    paint = "currentColor"
    if color:
        defs = (
            f'  <defs><linearGradient id="g" gradientUnits="userSpaceOnUse" x1="200" y1="200" x2="830" y2="830">'
            f'<stop offset="0" stop-color="{GRADIENT[0]}"/><stop offset="1" stop-color="{GRADIENT[1]}"/></linearGradient></defs>\n'
        )
        paint = "url(#g)"
    body = []
    for d, kind in parts:
        if kind == FILL:
            body.append(f'  <path d="{d}" fill="{paint}" fill-rule="nonzero"/>')
        else:
            body.append(
                f'  <path d="{d}" fill="none" stroke="{paint}" stroke-width="{kind[1]}" stroke-linecap="round" stroke-linejoin="round"/>'
            )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="{name}">\n'
        + defs + "\n".join(body) + "\n</svg>\n"
    )


def bbox(parts):
    """Square viewport around the artwork: min/max of every coordinate in the path data, padded by
    half the widest stroke. Control points can overshoot the curve slightly, which only adds margin."""
    import re
    xs, ys, pad = [], [], 0.0
    for d, kind in parts:
        # Drop each arc's radii and flags so the remaining numbers alternate x, y.
        flat = re.sub(r"A[-\d.]+,[-\d.]+ [-\d.]+ [01] [01] ", "L", d)
        nums = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", flat)]
        xs += nums[0::2]
        ys += nums[1::2]
        if kind != FILL:
            pad = max(pad, kind[1] / 2)
    x0, x1, y0, y1 = min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad
    side = max(x1 - x0, y1 - y0)
    return (x0 + x1 - side) / 2, (y0 + y1 - side) / 2, side


def kotlin(all_parts):
    lines = [
        "package dev.fanfly.wingslog.core.ui.brand",
        "",
        "// GENERATED by docs/branding/thing-icons/generate_icons.py — edit the shapes there, not here.",
        "",
        "/** One drawable piece of a glyph: a filled outline, or a round-capped stroke of [strokeWidth]. */",
        "data class GlyphPath(val data: String, val strokeWidth: Float? = null)",
        "",
        "/** A glyph's pieces plus the square viewport that frames its artwork tightly. */",
        "data class GlyphSpec(",
        "  val paths: List<GlyphPath>,",
        "  val viewportX: Float,",
        "  val viewportY: Float,",
        "  val viewportSize: Float,",
        ")",
        "",
        "/** The Thing glyphs, authored in a 1024x1024 box like the brand plane. */",
        "object ThingGlyphPaths {",
    ]
    for name, parts in all_parts.items():
        vx, vy, side = bbox(parts)
        lines.append(f"  val {name.upper()}: GlyphSpec = GlyphSpec(")
        lines.append("    paths = listOf(")
        for d, kind in parts:
            w = "" if kind == FILL else f", strokeWidth = {kind[1]}f"
            lines.append(f'      GlyphPath("{d}"{w}),')
        lines.append("    ),")
        lines.append(f"    viewportX = {vx:.1f}f,")
        lines.append(f"    viewportY = {vy:.1f}f,")
        lines.append(f"    viewportSize = {side:.1f}f,")
        lines.append("  )")
    lines.append("}")
    return "\n".join(lines) + "\n"


def contact_sheet(all_parts):
    cells = []
    for name, parts in all_parts.items():
        cells.append(
            f'<figure><div class="navy">{svg(name, parts, False)}</div>'
            f'<div class="white">{svg(name, parts, True)}</div><figcaption>{name}</figcaption></figure>'
        )
    return (
        "<meta charset='utf-8'><style>body{margin:0;background:#fff;font:14px system-ui}"
        "main{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;padding:16px;width:1000px}"
        "figure{margin:0}figure>div{padding:12px}svg{width:100%;height:auto;display:block}"
        ".navy{background:#001849;color:#A7C8FF;border-radius:14px 14px 0 0}"
        ".white{background:#F3F6FC;border-radius:0 0 14px 14px}figcaption{text-align:center;padding:6px}"
        "</style><main>" + "".join(cells) + "</main>"
    )


def main():
    all_parts = {name: fn() for name, fn in GLYPHS.items()}
    for name, parts in all_parts.items():
        open(os.path.join(HERE, f"{name}.svg"), "w").write(svg(name, parts, False))
        open(os.path.join(HERE, f"{name}-color.svg"), "w").write(svg(name, parts, True))
    open(os.path.join(HERE, "contact-sheet.html"), "w").write(contact_sheet(all_parts))
    os.makedirs(os.path.dirname(KT_OUT), exist_ok=True)
    open(KT_OUT, "w").write(kotlin(all_parts))
    print("wrote", len(all_parts), "glyphs;", os.path.relpath(KT_OUT, REPO))


if __name__ == "__main__":
    main()
