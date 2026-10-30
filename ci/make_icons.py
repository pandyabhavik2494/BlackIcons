#!/usr/bin/env python3
"""Turns the Arcticons black SVGs into the icon pack's drawables and appfilter.

Usage: make_icons.py <arcticons checkout> <app/src/main> [size]
Needs rsvg-convert (librsvg2-bin) and cwebp (webp) on the PATH.
"""
import os
import re
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

src, out = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 else 160

svg_dir = os.path.join(src, "icons", "black")
res = os.path.join(out, "res")
drawables = os.path.join(res, "drawable-nodpi")
os.makedirs(drawables, exist_ok=True)
os.makedirs(os.path.join(res, "xml"), exist_ok=True)
os.makedirs(os.path.join(out, "assets"), exist_ok=True)

KEYWORDS = set("""abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native new
package private protected public return short static strictfp super switch synchronized this throw throws
transient try void volatile while true false null as fun in is object typealias val var when""".split())
VALID = re.compile(r"^[a-z][a-z0-9_]*$")


def usable(name):
    return VALID.match(name) and name not in KEYWORDS


# The fallback for apps the pack doesn't cover: launchers shrink the app's own icon, cut it to a
# circle (iconmask), then lay iconupon on top: a dark veil and a black ring, so it reads as dark.
EXTRA = {
    "iconmask": '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">'
                '<path fill-rule="evenodd" d="M0 0H48V48H0Z M24 7.5a16.5 16.5 0 1 0 0.001 0Z"/></svg>',
    "iconupon": '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">'
                '<circle cx="24" cy="24" r="16.5" fill="#000" fill-opacity="0.62"/>'
                '<circle cx="24" cy="24" r="21.5" fill="none" stroke="#000" stroke-width="1"/></svg>',
}


def render(name, svg_path):
    png = os.path.join(tmp, name + ".png")
    webp = os.path.join(drawables, name + ".webp")
    try:
        subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), "-o", png, svg_path],
                       check=True, capture_output=True)
        subprocess.run(["cwebp", "-quiet", "-lossless", "-z", "6", png, "-o", webp],
                       check=True, capture_output=True)
        return name
    except subprocess.CalledProcessError as e:
        print(f"skipped {name}: {e.stderr.decode(errors='replace').strip()[:200]}")
        return None
    finally:
        if os.path.exists(png):
            os.remove(png)


tmp = tempfile.mkdtemp()
jobs = []
for f in sorted(os.listdir(svg_dir)):
    if f.endswith(".svg") and usable(f[:-4]):
        jobs.append((f[:-4], os.path.join(svg_dir, f)))
for name, svg in EXTRA.items():
    path = os.path.join(tmp, name + ".svg")
    with open(path, "w") as fh:
        fh.write(svg)
    jobs.append((name, path))

with ThreadPoolExecutor(max_workers=(os.cpu_count() or 2) * 2) as pool:
    made = {n for n in pool.map(lambda j: render(*j), jobs) if n}
icons = sorted(made - set(EXTRA))
print(f"rendered {len(icons)} icons")

appfilter_in = open(os.path.join(src, "app", "src", "main", "res", "xml", "appfilter.xml"), encoding="utf-8").read()
lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
         '\t<iconmask img1="iconmask"/>', '\t<iconupon img1="iconupon"/>', '\t<scale factor="0.62"/>']
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
