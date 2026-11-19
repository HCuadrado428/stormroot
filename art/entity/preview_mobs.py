"""Vista previa 3D (oblicua) de los mobs con sus texturas, montando las cajas de client/entity/*Model.java.

Uso: python3 art/entity/preview_mobs.py salida.png
"""
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "src/main/resources/assets/stormroot/textures/entity")


def part(offset=(0, 0, 0), rot=(0, 0, 0), boxes=(), children=()):
    return {"offset": offset, "rot": rot, "boxes": boxes, "children": children}


def box(u, v, x, y, z, w, h, d, mirror=False):
    return (u, v, x, y, z, w, h, d, mirror)


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def faces_of(b):
    """Caras de la caja: (rect de textura, origen, eje u, eje v) en coordenadas del modelo."""
    u, v, x0, y0, z0, w, h, d, mirror = b
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    rects = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    if mirror:
        rects["right"], rects["left"] = rects["left"], rects["right"]
    geo = {
        "front": ((x0, y0, z0), (w, 0, 0), (0, h, 0)),
        "back": ((x1, y0, z1), (-w, 0, 0), (0, h, 0)),
        "left": ((x1, y0, z0), (0, 0, d), (0, h, 0)),
        "right": ((x0, y0, z1), (0, 0, -d), (0, h, 0)),
        "top": ((x0, y0, z1), (w, 0, 0), (0, 0, -d)),
        "bottom": ((x0, y1, z0), (w, 0, 0), (0, 0, d)),
    }
    out = []
    for name, (o, eu, ev) in geo.items():
        out.append((rects[name], o, eu, ev, mirror))
    return out


def collect(p, m, out):
    ox, oy, oz = p["offset"]
    a, b, c = p["rot"]
    m = mat_mul(m, translate(ox, oy, oz))
    m = mat_mul(m, rz(c))
    m = mat_mul(m, ry(b))
    m = mat_mul(m, rx(a))
    for b_ in p["boxes"]:
        for rect, o, eu, ev, mirror in faces_of(b_):
            out.append((rect, m, o, eu, ev, mirror))
    for ch in p["children"]:
        collect(ch, m, out)


def render(parts, tex, yaw, scale):
    faces = []
    for p in parts:
        collect(p, translate(0, 0, 0), faces)
    view = mat_mul(rx(0.45), ry(yaw))  # mirar un poco desde arriba
    quads = []
    for rect, m, o, eu, ev, mirror in faces:
        tx, ty, tw, th = rect
        for j in range(th):
            for i in range(tw):
                ii = (tw - 1 - i) if mirror else i
                col = tex.getpixel((tx + ii, ty + j))
                if col[3] == 0:
                    continue
                pts = []
                for du, dv in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    q = (o[0] + eu[0] * du / tw + ev[0] * dv / th,
                         o[1] + eu[1] * du / tw + ev[1] * dv / th,
                         o[2] + eu[2] * du / tw + ev[2] * dv / th)
                    pts.append(apply(view, apply(m, q)))
                # normal para descartar caras de espaldas (la cámara mira hacia +z)
                ax = [pts[1][k] - pts[0][k] for k in range(3)]
                bx = [pts[3][k] - pts[0][k] for k in range(3)]
                nz = ax[0] * bx[1] - ax[1] * bx[0]
                if nz <= 0:
                    continue
                depth = sum(pt[2] for pt in pts) / 4
                ny = ax[2] * bx[0] - ax[0] * bx[2]
                light = 1.0 if ny < -0.01 * scale else 0.86
                quads.append((depth, pts, tuple(int(cc * light) for cc in col[:3])))
    xs = [pt[0] for q in quads for pt in q[1]]
    ys = [pt[1] for q in quads for pt in q[1]]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    W, H = int((maxx - minx) * scale) + 20, int((maxy - miny) * scale) + 20
    im = Image.new("RGB", (W, H), (236, 240, 244))
    dr = ImageDraw.Draw(im)
    for depth, pts, col in sorted(quads, key=lambda q: -q[0]):
        dr.polygon([((pt[0] - minx) * scale + 10, (pt[1] - miny) * scale + 10) for pt in pts], fill=col)
    return im


def sporeling():
    leg = lambda x, z: part((x, 22, z), boxes=[box(0, 22, -0.5, 0, -0.5, 1, 2, 1)])
    return [part((0, 22, 0), boxes=[box(0, 0, -3, -6, -3, 6, 6, 6)],
                 children=[part((0, -6, 0), boxes=[box(0, 12, -4, -2, -4, 8, 2, 8)])]),
            leg(2, -2), leg(-2, -2), leg(2, 2), leg(-2, 2)]


def weaver():
    parts = [part((0, 20, 1), boxes=[box(0, 0, -4, -4, -4, 8, 4, 8)]),
             part((0, 18, -3), boxes=[box(0, 12, -3, -2, -4, 6, 4, 4)],
                  children=[part(boxes=[box(24, 12, -4, -4, -5, 8, 2, 6)])])]
    for i in range(3):
        z = -2 + i * 3
        spread = (i - 1) * 0.45
        parts.append(part((4, 19, z), (0, spread, 0.55), [box(32, 0, 0, -0.5, -0.5, 7, 1, 1)]))
        parts.append(part((-4, 19, z), (0, -spread, -0.55), [box(32, 0, -7, -0.5, -0.5, 7, 1, 1, True)]))
    return parts


def guardian():
    return [part((0, 14, 0), boxes=[box(0, 0, -7, -14, -5, 14, 14, 10)]),
            part((0, 0, 0), boxes=[box(48, 0, -5, -8, -4, 10, 8, 8)],
                 children=[part(boxes=[box(0, 24, -8, -12, -8, 16, 4, 16)])]),
            part((9.5, 2, 0), boxes=[box(84, 0, -2.5, -2, -2.5, 5, 16, 5)]),
            part((-9.5, 2, 0), boxes=[box(84, 0, -2.5, -2, -2.5, 5, 16, 5, True)]),
            part((3.5, 14, 0), boxes=[box(104, 0, -2.5, 0, -2.5, 5, 10, 5)]),
            part((-3.5, 14, 0), boxes=[box(104, 0, -2.5, 0, -2.5, 5, 10, 5, True)])]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mobs_preview.png"
    rows = []
    for name, model, scale in (("sporeling", sporeling(), 26), ("weaver", weaver(), 18), ("mycelium_guardian", guardian(), 9)):
        tex = Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")
        views = [render(model, tex, yaw, scale) for yaw in (0.6, -0.6, math.pi + 0.6)]
        w = sum(v.width for v in views) + 20 * (len(views) - 1)
        h = max(v.height for v in views)
        row = Image.new("RGB", (w, h), (236, 240, 244))
        x = 0
        for v in views:
            row.paste(v, (x, h - v.height))
            x += v.width + 20
        rows.append(row)
    W = max(r.width for r in rows)
    H = sum(r.height for r in rows) + 30 * (len(rows) - 1)
    sheet = Image.new("RGB", (W, H), (236, 240, 244))
    y = 0
    for r in rows:
        sheet.paste(r, ((W - r.width) // 2, y))
        y += r.height + 30
    sheet.save(out)
    print(out, sheet.size)
