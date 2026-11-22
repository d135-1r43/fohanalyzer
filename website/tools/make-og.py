"""
Render the Open Graph card, assets/img/og.png, at 1200x630.

Everything here is taken from the site's own design tokens and from the app's
Logo.java geometry, so the card that appears in a link preview is the same
design as the page behind it. Re-run after a change to either — the wording is
in this file, not in the image.

    python3 -m venv .venv && .venv/bin/pip install Pillow
    .venv/bin/python tools/make-og.py

Pillow cannot read the subset WOFF2 files in assets/fonts, so the text is set
from the original TTFs in the application repository. It is expected next to
this one; point FOH_FONTS at the directory if it lives somewhere else.
"""

import math
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1200, 630
PAD = 72

BG = (7, 10, 15)
PANEL = (13, 18, 27)
LINE = (28, 39, 51)
TXT = (231, 238, 246)
TXT_DIM = (133, 149, 166)
TXT_MUT = (91, 106, 122)
CYAN = (34, 211, 238)
LIME = (163, 230, 53)
AMBER = (245, 165, 36)

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = "src/main/resources/com/fohanalyzer/fonts"

# The app repository was renamed from fohanalyzer-java to fohanalyzer, so a
# fresh sibling checkout and one made before the rename have different
# directory names. Accept either rather than turning an older clone into an
# error that only FOH_FONTS can clear. The new name is what the failure
# message names when neither is there.
def sibling_fonts():
	# The site now lives in website/ of the app repository itself, so its
	# parent is the first place to look.
	candidates = [ROOT.parent / RESOURCES]
	candidates += [ROOT.parent.parent / name / RESOURCES for name in ("fohanalyzer", "fohanalyzer-java")]
	for candidate in candidates:
		if (candidate / "Barlow-Bold.ttf").is_file():
			return candidate
	return ROOT.parent / "fohanalyzer" / RESOURCES

FONTS = Path(os.environ["FOH_FONTS"]) if os.environ.get("FOH_FONTS") else sibling_fonts()
OUT = ROOT / "assets/img/og.png"

if not (FONTS / "Barlow-Bold.ttf").is_file():
	sys.exit(f"no fonts at {FONTS}\nset FOH_FONTS to the app's resources/com/fohanalyzer/fonts")

barlow_b = lambda s: ImageFont.truetype(str(FONTS / "Barlow-Bold.ttf"), s)
mono_b = lambda s: ImageFont.truetype(str(FONTS / "IBMPlexMono-Bold.ttf"), s)
mono_r = lambda s: ImageFont.truetype(str(FONTS / "IBMPlexMono-Regular.ttf"), s)


def lerp(a, b, t):
	t = max(0.0, min(1.0, t))
	return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def ramp(p):
	"""The hero's cyan -> lime -> amber sweep, as used by <foh-spectrum>."""
	return lerp(CYAN, LIME, p / 0.55) if p < 0.55 else lerp(LIME, AMBER, (p - 0.55) / 0.45)


img = Image.new("RGB", (W, H), BG)

# -- glow -------------------------------------------------------------------
# The hero's radial wash: two soft ellipses, blurred hard, laid over the base.
glow = Image.new("RGB", (W, H), BG)
g = ImageDraw.Draw(glow)
g.ellipse([-200, -420, 780, 300], fill=(16, 45, 58))
g.ellipse([520, -460, 1400, 240], fill=(30, 44, 22))
img = Image.blend(img, glow.filter(ImageFilter.GaussianBlur(150)), 0.85)

d = ImageDraw.Draw(img)

# -- logo -------------------------------------------------------------------
# com.fohanalyzer.ui.controls.Logo, 40x40 artwork scaled up. JavaFX arcWidth is
# a diameter, so its 18 and 3 become radii of 9 and 1.5.
S = 72 / 40.0
LX, LY = PAD, PAD - 4
d.rounded_rectangle([LX + 1 * S, LY + 1 * S, LX + 39 * S, LY + 39 * S],
                    radius=9 * S, fill=(14, 20, 29), outline=(31, 42, 55), width=2)
for x, y, w, h, c in ((8, 22, 4.5, 10, CYAN), (14.5, 14, 4.5, 18, CYAN),
                      (21, 9, 4.5, 23, LIME), (27.5, 18, 4.5, 14, AMBER)):
	d.rounded_rectangle([LX + x * S, LY + y * S, LX + (x + w) * S, LY + (y + h) * S],
	                    radius=1.5 * S, fill=c)

tx = PAD + 72 * S + 22
d.text((tx, LY + 10), "FOHanalyzer", font=barlow_b(40), fill=TXT)
d.text((tx + 2, LY + 58), "R T A  ·  S P E C T R O G R A P H  ·  S P L", font=mono_r(15), fill=TXT_MUT)

# -- headline ---------------------------------------------------------------
# "Everything FOH" plain, "needs to see." in the accent gradient — the same
# split the hero makes.
h1 = barlow_b(92)
d.text((PAD, 232), "Everything FOH", font=h1, fill=TXT)

line2, y2 = "needs to see.", 330
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).text((PAD, y2), line2, font=h1, fill=255)
grad = Image.new("RGB", (W, H))
gd = ImageDraw.Draw(grad)
x1 = PAD + int(d.textlength(line2, font=h1))
for x in range(W):
	gd.line([(x, 0), (x, H)], fill=ramp(max(0.0, min(1.0, (x - PAD) / max(1, x1 - PAD)))))
img.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(img)

# -- strapline --------------------------------------------------------------
d.text((PAD, 456), "RTA, spectrograph and SPL meter for front of house.",
       font=barlow_b(26), fill=TXT_DIM)

# -- chip -------------------------------------------------------------------
label = "OPEN SOURCE  ·  GPL-3.0  ·  FREE FOREVER"
f = mono_b(17)
tw = d.textlength(label, font=f)
cx, cy = PAD, 516
d.rounded_rectangle([cx, cy, cx + tw + 34, cy + 42], radius=8,
                    fill=(16, 26, 20), outline=(80, 120, 40), width=2)
d.text((cx + 17, cy + 11), label, font=f, fill=LIME)

# -- spectrum ---------------------------------------------------------------
# The illustration from the hero: a log-tilted bar display with peak caps,
# and the app's split view under it — a spectrograph on the same columns.
# Confined to the right column — the headline runs to roughly x=740, so the
# bars start well clear of it rather than overprinting the words.
BARS, BW, GAP = 20, 12, 6
right = W - PAD
base_y, max_h = 372, 190

# The app's spectrograph palette, quiet to loud.
HEAT = [(12, 28, 58), (22, 92, 140), (30, 170, 170), (120, 214, 80), (238, 226, 90)]


def heat(v):
	v = max(0.0, min(1.0, v)) * (len(HEAT) - 1)
	k = min(int(v), len(HEAT) - 2)
	return lerp(HEAT[k], HEAT[k + 1], v - k)


def level(p):
	tilt = 0.92 - 0.55 * pow(p, 1.35) + 0.10 * math.exp(-pow((p - 0.22) / 0.16, 2))
	wob = 0.10 * math.sin(p * 11.0) + 0.06 * math.sin(p * 23.0 + 1.1)
	return max(0.06, min(1.0, tilt + wob))


ROWS, RH, top_s = 12, 9, base_y + 14
for i in range(BARS):
	p = i / (BARS - 1)
	x = right - (BARS - i) * (BW + GAP)
	for r in range(ROWS):
		drift = 0.08 * math.sin(r * 0.9 + p * 7.0) + 0.05 * math.sin(r * 2.3 - p * 13.0)
		# A ring that comes and goes at one column, the way the app shows one.
		ring = 0.35 if i == 13 and r in (2, 3, 7, 8, 9) else 0.0
		col = heat(level(p) * 0.95 + drift + ring - 0.05)
		y = top_s + r * RH
		d.rectangle([x, y, x + BW + GAP - 1, y + RH - 1], fill=lerp(col, BG, 0.25))

for i in range(BARS):
	p = i / (BARS - 1)
	v = level(p)
	bh = v * max_h
	x = right - (BARS - i) * (BW + GAP)
	col = ramp(p)
	# Top-bright to bottom-faint, as the canvas gradient does.
	for k in range(int(bh)):
		t = k / max(1, bh)
		d.line([(x, base_y - bh + k), (x + BW, base_y - bh + k)],
		       fill=lerp(col, BG, 0.15 + 0.75 * t))
	d.rectangle([x, base_y - bh - 4, x + BW, base_y - bh - 2], fill=lerp(col, BG, 0.45))

# -- zone strip -------------------------------------------------------------
# Engine.ZONES, at the foot of the card, mirroring the app's legend.
zones = [(0.7, (59, 91, 255)), (1.3, (47, 155, 255)), (1.0, (25, 198, 198)),
         (1.6, (47, 208, 122)), (0.8, (163, 230, 53)), (0.8, (245, 165, 36)),
         (1.1, (245, 87, 93))]
total = sum(w for w, _ in zones)
zx, zy, zw = PAD, H - 34, W - 2 * PAD
for w, c in zones:
	seg = zw * w / total
	d.rectangle([zx, zy, zx + seg, zy + 3], fill=lerp(c, BG, 0.35))
	zx += seg

d.text((right - d.textlength("fohanalyzer.com", font=mono_r(18)), 540),
       "fohanalyzer.com", font=mono_r(18), fill=TXT_MUT)

img.save(str(OUT), "PNG", optimize=True)
print("wrote", OUT, img.size)
