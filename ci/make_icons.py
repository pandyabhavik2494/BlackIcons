#!/usr/bin/env python3
"""Turns the Arcticons SVGs into the icon pack's drawables and appfilter.

Each icon is Arcticons' white line drawing, drawn bolder, on a black circle.
Usage: make_icons.py <arcticons checkout> <app/src/main> [size]
Needs: pip install cairosvg pillow
"""
import io
import os
import re
import sys
from concurrent.futures import ProcessPoolExecutor

import cairosvg
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 else 192

STROKE = 2.6   # line width in the source's 48-unit grid (Arcticons draws at 1)
SCALE = 0.56   # how much of the circle the drawing fills

svg_dir = os.path.join(src, "icons", "white")
res = os.path.join(out, "res")
drawables = os.path.join(res, "drawable-nodpi")

KEYWORDS = set("""abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native new
package private protected public return short static strictfp super switch synchronized this throw throws
transient try void volatile while true false null as fun in is object typealias val var when""".split())
VALID = re.compile(r"^[a-z][a-z0-9_]*$")


def usable(name):
    return VALID.match(name) and name not in KEYWORDS


def on_disc(svg):
    svg = re.sub(r"<\?xml[^>]*\?>", "", svg)
    svg = re.sub(r'stroke-width\s*:\s*[^;}"]*;?', "", svg)
    svg = re.sub(r'stroke-width="[^"]*"', "", svg)
    svg = svg.replace("stroke:#fff", f"stroke:#fff;stroke-width:{STROKE}px")
    svg = svg.replace('stroke="#fff"', f'stroke="#fff" stroke-width="{STROKE}"')
    pad = (48 - 48 * SCALE) / 2
    inner = re.sub(r"^\s*<svg", f'<svg x="{pad}" y="{pad}" width="{48 * SCALE}" height="{48 * SCALE}"', svg.strip(), count=1)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="48" height="48">'
            '<circle cx="24" cy="24" r="24" fill="#000"/>' + inner + "</svg>")


# For apps the pack doesn't cover, launchers draw iconback and shrink the app's own icon onto it.
EXTRA = {
    "iconback": '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="48" height="48">'
                '<circle cx="24" cy="24" r="24" fill="#000"/></svg>',
}


def render(job):
    name, svg = job
    try:
        png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
        Image.open(io.BytesIO(png)).save(os.path.join(drawables, name + ".webp"), "WEBP", lossless=True, method=4)
        return name
    except Exception as e:  # a broken SVG costs one icon, not the build
        print(f"skipped {name}: {e}")
        return None


if __name__ == "__main__":
    os.makedirs(drawables, exist_ok=True)
    os.makedirs(os.path.join(res, "xml"), exist_ok=True)
    os.makedirs(os.path.join(out, "assets"), exist_ok=True)

    jobs = [(n, s) for n, s in EXTRA.items()]
    for f in sorted(os.listdir(svg_dir)):
        if f.endswith(".svg") and usable(f[:-4]):
            with open(os.path.join(svg_dir, f), encoding="utf-8") as fh:
                jobs.append((f[:-4], on_disc(fh.read())))

    with ProcessPoolExecutor() as pool:
        made = {n for n in pool.map(render, jobs, chunksize=64) if n}
    # Icons made for Bhavik's own apps that Arcticons doesn't have: ci/custom/<name>.png, already on the circle.
    custom = os.path.join(os.path.dirname(os.path.abspath(__file__)), "custom")
    if os.path.isdir(custom):
        for f in sorted(os.listdir(custom)):
            if f.endswith(".png") and usable(f[:-4]):
                im = Image.open(os.path.join(custom, f)).convert("RGBA").resize((size, size), Image.LANCZOS)
                im.save(os.path.join(drawables, f[:-4] + ".webp"), "WEBP", lossless=True, method=4)
                made.add(f[:-4])
    icons = sorted(made - set(EXTRA))
    print(f"rendered {len(icons)} icons")

    appfilter_in = open(os.path.join(src, "app", "src", "main", "res", "xml", "appfilter.xml"), encoding="utf-8").read()
    # Our own matches for Bhavik's apps whose activity names Arcticons doesn't list (yet). Listed first so they win.
    extra = os.path.join(os.path.dirname(os.path.abspath(__file__)), "extra_appfilter.xml")
    if os.path.exists(extra):
        appfilter_in = open(extra, encoding="utf-8").read() + appfilter_in

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
             '\t<iconback img1="iconback"/>', '\t<scale factor="0.55"/>']
    items = 0
    for m in re.finditer(r"<(item|calendar)\s[^>]*?/>", appfilter_in):
        tag = m.group(0)
        if m.group(1) == "item":
            d = re.search(r'drawable="([^"]+)"', tag)
            if not d or d.group(1) not in made:
                continue
            items += 1
        else:
            p = re.search(r'prefix="([^"]+)"', tag)
            if not p or not all(f"{p.group(1)}{day}" in made for day in range(1, 32)):
                continue
        lines.append("\t" + tag)
    lines.append("</resources>")
    appfilter = "\n".join(lines) + "\n"
    for path in (os.path.join(res, "xml", "appfilter.xml"), os.path.join(out, "assets", "appfilter.xml")):
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(appfilter)
    print(f"appfilter: {items} app activities")

    with open(os.path.join(res, "xml", "drawable.xml"), "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n\t<version>1</version>\n\t<category title="All"/>\n')
        fh.writelines(f'\t<item drawable="{n}"/>\n' for n in icons)
        fh.write("</resources>\n")
