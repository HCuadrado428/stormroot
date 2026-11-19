"""Genera las texturas de los mobs de Stormroot (esporín, tejedor y guardián del micelio).

Cada textura sigue la UV de caja de su modelo en client/entity/*Model.java: para una caja de w×h×d en (u, v),
arriba (u+d, v) w×d, abajo (u+d+w, v) w×d, y en la fila v+d: derecha (u) d×h, delante (u+d) w×h,
izquierda (u+d+w) d×h, detrás (u+2d+w) w×h.

Uso: python3 art/entity/make_mob_textures.py [carpeta_de_vistas]
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/stormroot/textures/entity")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


class Tex:
    def __init__(self, w, h, seed):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.r = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y))

    def box(self, u, v, w, h, d, painter):
        """Pinta las 6 caras de una caja. painter(face, x, y, fw, fh) -> color."""
        faces = {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    self.px(fx + x, fy + y, painter(face, x, y, fw, fh))
        return faces

    def save(self, name):
        self.im.save(os.path.join(OUT, name + ".png"))


def side_light(face):
    return {"top": 1.12, "bottom": 0.72, "front": 1.0, "back": 0.9, "right": 0.86, "left": 0.94}[face]


# ---------------------------------------------------------------------------------------------- esporín
def sporeling():
    t = Tex(32, 32, 11)
    r = t.r
    skin = [hexc(c) for c in ["#e4edc0", "#d2dfa7", "#bccd8e", "#a3b677", "#8a9e62"]]
    spot = hexc("#f6ffe4")

    def body(face, x, y, w, h):
        if face == "top":
            return skin[0] if r.random() > 0.25 else skin[1]
        if face == "bottom":
            return skin[4] if r.random() > 0.3 else skin[3]
        i = 1 + int(y * 3 / h)  # más oscuro hacia abajo
        if r.random() < 0.18:
            i = min(4, i + 1)
        if y == 0:
            i = 0
        c = skin[min(4, i)]
        if face in ("left", "right", "back") and r.random() < 0.06 and 0 < y < h - 1:
            c = spot
        return c

    f = t.box(0, 0, 6, 6, 6, body)
    fx, fy = f["front"][0], f["front"][1]
    eye, white = hexc("#1d2516"), hexc("#ffffff")
    for ex in (fx + 1, fx + 4):
        for dx in (0, 1):
            for dy in (1, 2):
                t.px(ex + dx, fy + dy, eye)
        t.px(ex, fy + 1, white)
    blush = hexc("#e8b98f")
    t.px(fx, fy + 3, blush)
    t.px(fx + 5, fy + 3, blush)
    t.px(fx + 2, fy + 4, hexc("#6d7f48"))
    t.px(fx + 3, fy + 4, hexc("#6d7f48"))

    cap = [hexc(c) for c in ["#b996cf", "#9a78b3", "#7f5f99", "#644a7c", "#4d3961"]]
    gill = [hexc("#ead9c2"), hexc("#cdb79c"), hexc("#ab9579")]
    spots = {(1, 1), (5, 1), (3, 3), (6, 4), (1, 5), (4, 6), (2, 6)}

    def capp(face, x, y, w, h):
        if face == "top":
            if (x, y) in spots:
                return spot
            if (x - 1, y) in spots and r.random() < 0.5:
                return hexc("#d7e9b8")
            dist = abs(x - 3.5) + abs(y - 3.5)
            i = 0 if dist < 2 else 1 if dist < 4.5 else 2
            if r.random() < 0.2:
                i += 1
            return cap[i]
        if face == "bottom":
            cx, cy = abs(x - 3.5), abs(y - 3.5)
            if cx < 1.5 and cy < 1.5:
                return gill[2]
            return gill[0] if (x + y) % 2 == 0 else gill[1]
        # borde del sombrero: claro arriba, oscuro abajo, con alguna mancha
        if y == 0:
            return spot if r.random() < 0.15 else cap[1]
        return cap[3]

    t.box(0, 12, 8, 2, 8, capp)

    def leg(face, x, y, w, h):
        return [skin[3], skin[4], hexc("#5f6e3f")][min(2, y)] if face not in ("top", "bottom") else skin[4]

    t.box(0, 22, 1, 2, 1, leg)
    t.save("sporeling")
    return t


# ---------------------------------------------------------------------------------------------- tejedor
def weaver():
    t = Tex(64, 32, 23)
    r = t.r
    fur = [hexc(c) for c in ["#e7e0cd", "#d4ccb5", "#bdb39a", "#a39880", "#857a64", "#665c4a"]]
    thread = hexc("#f4f1e6")
    moss = hexc("#9db26a")
    mark = [hexc("#6e4f7e"), hexc("#553c63")]

    def body(face, x, y, w, h):
        if face == "top":
            # abdomen peludo con una marca de chevrones morados y hilos
            pattern = ["........",
                       "...##...",
                       "..#oo#..",
                       ".#o..o#.",
                       "..#oo#..",
                       "...##...",
                       "..#..#..",
                       "........"]
            ch = pattern[y][x]
            if ch == "#":
                return mark[1]
            if ch == "o":
                return mark[0]
            if r.random() < 0.08:
                return moss
            if r.random() < 0.1:
                return thread
            return fur[0] if r.random() > 0.35 else fur[1]
        if face == "bottom":
            return fur[4] if r.random() > 0.3 else fur[5]
        i = 1 + y
        if r.random() < 0.25:
            i += 1
        c = fur[min(5, i)]
        if y == 0 and r.random() < 0.3:
            c = thread
        if r.random() < 0.05:
            c = moss
        return c

    t.box(0, 0, 8, 4, 8, body)

    def head(face, x, y, w, h):
        if face == "top":
            return fur[1]
        if face == "bottom":
            return fur[4]
        c = fur[1 + min(3, y)] if r.random() > 0.2 else fur[2]
        return c

    f = t.box(0, 12, 6, 4, 4, head)
    fx, fy = f["front"][0], f["front"][1]
    glow, glow2 = hexc("#d6ff8a"), hexc("#8fd14a")
    # cuatro ojos que brillan (dos grandes, dos pequeños)
    for ex in (fx + 1, fx + 4):
        t.px(ex, fy + 1, glow)
        t.px(ex, fy + 2, glow2)
    t.px(fx + 0, fy + 0, glow2)
    t.px(fx + 5, fy + 0, glow2)
    # quelíceros
    fang = hexc("#4a3f33")
    t.px(fx + 2, fy + 3, fang)
    t.px(fx + 3, fy + 3, fang)

    cap = [hexc(c) for c in ["#a98db9", "#8b6f9f", "#705685", "#57416a", "#433251"]]
    spot = hexc("#eaf6cf")
    gill = [hexc("#e2d2bc"), hexc("#c3ad92")]
    spots = {(1, 1), (5, 2), (3, 4), (6, 0), (2, 3)}

    def capp(face, x, y, w, h):
        if face == "top":
            if (x, y) in spots:
                return spot
            i = 0 if abs(x - 3.5) + abs(y - 2.5) < 2.5 else 1
            if r.random() < 0.25:
                i += 1
            return cap[i]
        if face == "bottom":
            return gill[(x + y) % 2]
        return cap[1] if y == 0 else cap[3]

    t.box(24, 12, 8, 2, 6, capp)

    def leg(face, x, y, w, h):
        if face in ("top", "bottom", "front", "back"):
            # patas a franjas: claro, oscuro y punta oscura
            if x >= w - 2:
                return fur[5]
            if x in (2, 5):
                return fur[4]
            return fur[2] if face in ("top", "front") else fur[3]
        return fur[3]

    t.box(32, 0, 7, 1, 1, leg)
    t.save("weaver")
    return t


# ---------------------------------------------------------------------------------------------- guardián
def guardian():
    t = Tex(128, 64, 47)
    r = t.r
    bark = [hexc(c) for c in ["#8a6a4c", "#765a40", "#634a34", "#4f3a29", "#3c2c1f"]]
    vein = hexc("#b9f06c")
    vein_dim = hexc("#79a84a")
    moss = [hexc("#6f8f3d"), hexc("#587533")]

    def bark_px(face, x, y, w, h, cols):
        # corteza con surcos verticales y algo de musgo arriba
        if face == "top":
            ring = int(max(abs(x - w / 2 + 0.5), abs(y - h / 2 + 0.5)))
            c = bark[1] if ring % 2 else bark[0]
            if r.random() < 0.25:
                c = moss[r.randrange(2)]
            return c
        if face == "bottom":
            return bark[4] if r.random() > 0.3 else bark[3]
        groove = cols.get(x, 0)
        i = 1 + groove
        if r.random() < 0.22:
            i += r.choice((-1, 1))
        i += int(y * 1.5 / h)
        c = bark[max(0, min(4, i))]
        if y < 2 and r.random() < 0.45:
            c = moss[r.randrange(2)]
        return c

    def grooves(w):
        cols = {}
        for x in range(w):
            cols[x] = 2 if r.random() < 0.28 else 0
        return cols

    groove_cache = {}

    def make(tag):
        def painter(face, x, y, w, h):
            key = (tag, face)
            if key not in groove_cache:
                groove_cache[key] = grooves(w)
            return bark_px(face, x, y, w, h, groove_cache[key])
        return painter

    # cuerpo
    f = t.box(0, 0, 14, 14, 10, make("body"))
    fx, fy, fw, fh = f["front"]
    # vetas que brillan desde un núcleo en el pecho
    core = (fx + 7, fy + 5)
    paths = [[(0, 0), (-1, 1), (-2, 2), (-3, 2), (-4, 3), (-5, 4), (-5, 5), (-6, 6)],
             [(0, 0), (1, 1), (2, 1), (3, 2), (4, 3), (4, 4), (5, 5), (6, 7)],
             [(0, 0), (0, 1), (-1, 2), (-1, 3), (0, 4), (0, 5), (1, 6), (1, 7), (0, 8)],
             [(0, 0), (-1, -1), (-2, -2), (-2, -3), (-3, -4)],
             [(0, 0), (1, -1), (2, -2), (3, -2), (4, -3), (5, -4)]]
    for p in paths:
        for k, (dx, dy) in enumerate(p):
            t.px(core[0] + dx, core[1] + dy, vein if k < len(p) - 2 else vein_dim)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            t.px(core[0] + dx, core[1] + dy, vein if (dx == 0 or dy == 0) else vein_dim)
    t.px(core[0], core[1], hexc("#f4ffd6"))
    # vetas sueltas en los lados y detrás
    for face in ("left", "right", "back"):
        sx, sy, sw, sh = f[face]
        x = sx + r.randrange(2, sw - 2)
        for y in range(sy + 2, sy + sh - 1):
            t.px(x, y, vein_dim if r.random() < 0.6 else vein)
            if r.random() < 0.35:
                x = max(sx + 1, min(sx + sw - 2, x + r.choice((-1, 1))))
    # setas pequeñas en los hombros (en la cara de arriba)
    tx, ty, tw, td = f["top"]
    for (dx, dy) in ((1, 1), (12, 2), (2, 7), (11, 8)):
        t.px(tx + dx, ty + dy, hexc("#c45b4a"))
        t.px(tx + dx, ty + dy - 1 if dy > 0 else ty, hexc("#e7d8c0"))

    # cabeza
    f = t.box(48, 0, 10, 8, 8, make("head"))
    hx, hy, hw, hh = f["front"]
    # ceja oscura, ojos hundidos que brillan y boca
    for x in range(hw):
        t.px(hx + x, hy + 2, bark[4] if 1 <= x <= 8 else bark[3])
    for ex in (hx + 2, hx + 6):
        t.px(ex, hy + 3, bark[4])
        t.px(ex + 1, hy + 3, bark[4])
        t.px(ex, hy + 4, vein)
        t.px(ex + 1, hy + 4, hexc("#f4ffd6"))
    for x in range(3, 7):
        t.px(hx + x, hy + 6, bark[4])
    t.px(hx + 4, hy + 7, bark[4])
    t.px(hx + 5, hy + 7, bark[4])
    t.px(hx + 3, hy + 7, vein_dim)
    t.px(hx + 6, hy + 7, vein_dim)

    # sombrero (seta enorme de repisa)
    cap = [hexc(c) for c in ["#c7714f", "#ad5c40", "#924934", "#763a2b", "#5b2c22"]]
    spot = hexc("#efe2c9")
    gill = [hexc("#e2cfae"), hexc("#c3aa86"), hexc("#9d8463")]
    spots = set()
    while len(spots) < 14:
        s = (r.randrange(1, 15), r.randrange(1, 15))
        if all(abs(s[0] - a) + abs(s[1] - b) > 2 for a, b in spots):
            spots.add(s)

    def capp(face, x, y, w, h):
        if face == "top":
            if (x, y) in spots:
                return spot
            if (x - 1, y) in spots or (x, y - 1) in spots:
                return cap[0] if r.random() < 0.6 else cap[1]
            d = max(abs(x - 7.5), abs(y - 7.5))
            i = 1 if d < 3 else 2 if d < 6 else 3
            if r.random() < 0.2:
                i += r.choice((-1, 1))
            return cap[max(0, min(4, i))]
        if face == "bottom":
            cx, cy = x - 7.5, y - 7.5
            if abs(cx) < 5 and abs(cy) < 4:  # donde entra la cabeza
                return gill[2]
            # láminas en radios
            if abs(cx) > abs(cy):
                return gill[0] if y % 2 else gill[1]
            return gill[0] if x % 2 else gill[1]
        # borde: arriba claro con motas, luego más oscuro, último píxel casi negro
        if y == 0:
            return spot if r.random() < 0.18 else cap[1]
        if y == h - 1:
            return cap[4]
        return cap[2] if r.random() > 0.2 else cap[3]

    t.box(0, 24, 16, 4, 16, capp)

    # brazos: corteza que acaba en raíces claras (manos)
    arm_body = make("arm")

    def arm(face, x, y, w, h):
        if face == "bottom":
            return hexc("#d9c7a3") if (x + y) % 2 else hexc("#bca681")
        if face != "top" and y >= h - 3:
            fingers = (x % 2 == 0)
            if y == h - 1:
                return hexc("#bca681") if fingers else hexc("#7c6748")
            return hexc("#d9c7a3") if fingers else hexc("#a58f6a")
        c = arm_body(face, x, y, w, h)
        if face == "front" and x == 2 and 4 <= y <= 9:
            c = vein_dim if y % 3 else vein
        return c

    t.box(84, 0, 5, 16, 5, arm)

    # piernas: tocón con raíces en el pie
    leg_body = make("leg")

    def leg(face, x, y, w, h):
        if face not in ("top", "bottom") and y >= h - 2:
            return bark[4] if (x + y) % 2 else bark[3]
        return leg_body(face, x, y, w, h)

    t.box(104, 0, 5, 10, 5, leg)
    t.save("mycelium_guardian")
    return t


def preview(texs, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for name, t, s in texs:
        bg = Image.new("RGBA", t.im.size, (255, 255, 255, 255))
        for y in range(t.im.height):
            for x in range(t.im.width):
                if (x + y) % 2:
                    bg.putpixel((x, y), (232, 232, 232, 255))
        bg.alpha_composite(t.im)
        bg.resize((t.im.width * s, t.im.height * s), Image.NEAREST).save(os.path.join(out_dir, name + ".png"))


if __name__ == "__main__":
    texs = [("sporeling", sporeling(), 10), ("weaver", weaver(), 8), ("mycelium_guardian", guardian(), 5)]
    if len(sys.argv) > 1:
        preview(texs, sys.argv[1])
