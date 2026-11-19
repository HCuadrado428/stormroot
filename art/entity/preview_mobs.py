"""Vista previa 3D (oblicua) de los mobs con sus texturas, montando las cajas de client/entity/*Model.java.

Uso: python3 art/entity/preview_mobs.py salida.png
"""
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "src/main/resources/assets/stormroot/textures/entity")


def part(offset=(0, 0, 0), rot=(0, 0, 0), boxes=(), children=(), name=""):
    return {"offset": offset, "rot": rot, "boxes": boxes, "children": list(children), "name": name}


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


Q, E = math.pi / 4, math.pi / 8


def shroom(name, size, x, y, z, tx, tz, stem_uv, cap_uv):
    sw = 2 if size == 0 else 1
    sh = (3, 2, 1)[size]
    cw = (5, 3, 2)[size]
    ch = 2 if size == 0 else 1
    return part((x, y, z), (tx, 0, tz), [box(stem_uv[0], stem_uv[1], -sw / 2, -sh, -sw / 2, sw, sh, sw),
                                          box(cap_uv[0], cap_uv[1], -cw / 2, -sh - ch, -cw / 2, cw, ch, cw)], name=name)


def sporeling():
    """Creeper en miniatura (mismas piezas que CreeperModel) con sombrero de seta."""
    cap = part(boxes=[box(0, 32, -6, -10, -6, 12, 2, 12), box(0, 46, -4, -13, -4, 8, 3, 8)], name="cap")
    leg = lambda n, x, z: part((x, 18, z), boxes=[box(0, 16, -2, 0, -2, 4, 6, 4)], name=n)
    return [part((0, 6, 0), boxes=[box(0, 0, -4, -8, -4, 8, 8, 8)], children=[cap], name="head"),
            part((0, 6, 0), boxes=[box(16, 16, -4, 0, -2, 8, 12, 4)], name="body"),
            leg("right_hind_leg", -2, 4), leg("left_hind_leg", 2, 4), leg("right_front_leg", -2, -4), leg("left_front_leg", 2, -4)]


def weaver():
    """Araña vanilla (SpiderModel) con setas; las patas en la postura de reposo que les da setupAnim."""
    S = {0: ((20, 32), (0, 32)), 1: ((40, 32), (28, 32)), 2: ((52, 32), (44, 32))}
    m = lambda n, size, x, y, z, tx, tz: shroom(n, size, x, y, z, tx, tz, *S[size])
    head = part((0, 15, -3), boxes=[box(32, 4, -4, -4, -8, 8, 8, 8)], name="head",
                children=[m("mushroom_head_a", 1, 1.5, -4, -5, 0.1, 0.25), m("mushroom_head_b", 2, -2.5, -4, -2, -0.1, -0.2)])
    body1 = part((0, 15, 9), boxes=[box(0, 12, -5, -4, -6, 10, 8, 12)], name="body1",
                 children=[m("mushroom_big", 0, 1.5, -4, 1, -0.1, 0.12), m("mushroom_back_a", 1, -2.5, -4, -2.5, 0.05, -0.2),
                           m("mushroom_back_b", 1, -1, -4, 4, 0.2, 0.05), m("mushroom_back_c", 2, 3.5, -4, -3.5, -0.15, 0.3),
                           m("mushroom_side", 2, -5, -1, 2, 0, -1.3)])
    parts = [head, part((0, 15, 0), boxes=[box(0, 0, -3, -3, -3, 6, 6, 6)], name="body0"), body1]
    legs = [("hind_leg", 2, Q, Q), ("middle_hind_leg", 1, 0.58119464, E), ("middle_front_leg", 0, 0.58119464, -E), ("front_leg", -1, Q, -Q)]
    for n, z, zr, yr in legs:
        rk = [m("mushroom_leg_b", 2, -8, -1, 0, 0, 0.5)] if n == "middle_hind_leg" else []
        lk = [m("mushroom_leg_a", 2, 7, -1, 0, 0, -0.5)] if n == "front_leg" else []
        parts.append(part((-4, 15, z), (0, yr, -zr), [box(18, 0, -15, -1, -1, 16, 2, 2)], rk, "right_" + n))
        parts.append(part((4, 15, z), (0, -yr, zr), [box(18, 0, -1, -1, -1, 16, 2, 2, True)], lk, "left_" + n))
    return parts


def guardian():
    """Gólem de hierro (IronGolemModel) de corteza, con sombrero y setas en los hombros."""
    big = lambda n, x, z, tx, tz: part((x, -2, z), (tx, 0, tz), [box(108, 0, -1, -3, -1, 2, 3, 2), box(84, 0, -3, -5, -3, 6, 2, 6)], name=n)
    small = lambda n, x, z, tx, tz: part((x, -2, z), (tx, 0, tz), [box(100, 10, -0.5, -2, -0.5, 1, 2, 1), box(84, 10, -2, -3, -2, 4, 1, 4)], name=n)
    cap = part(boxes=[box(0, 90, -8, -14, -9.5, 16, 2, 16), box(64, 96, -5, -18, -6.5, 10, 4, 10)], name="cap")
    return [part((0, -7, -2), boxes=[box(0, 0, -4, -12, -5.5, 8, 10, 8), box(24, 0, -1, -5, -7.5, 2, 4, 2)], children=[cap], name="head"),
            part((0, -7, 0), boxes=[box(0, 40, -9, -2, -6, 18, 12, 11), box(0, 70, -4.5, 10, -3, 9, 5, 6)], name="body",
                 children=[big("mushroom_shoulder", 6, -0.5, 0.05, 0.2), small("mushroom_shoulder_small", -6.5, 1, -0.1, -0.25),
                           small("mushroom_back", 2.5, 3.5, 0.3, 0.1)]),
            part((0, -7, 0), boxes=[box(60, 21, -13, -2.5, -3, 4, 30, 6)], name="right_arm"),
            part((0, -7, 0), boxes=[box(60, 58, 9, -2.5, -3, 4, 30, 6)], name="left_arm"),
            part((-4, 11, 0), boxes=[box(37, 0, -3.5, -3, -3, 6, 16, 5)], name="right_leg"),
            part((5, 11, 0), boxes=[box(60, 0, -3.5, -3, -3, 6, 16, 5, True)], name="left_leg")]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mobs_preview.png"
    rows = []
    for name, model, scale in (("sporeling", sporeling(), 16), ("weaver", weaver(), 14), ("mycelium_guardian", guardian(), 8)):
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
