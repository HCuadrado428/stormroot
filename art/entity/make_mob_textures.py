"""Genera las texturas de los mobs de Stormroot y sus capas de brillo.

- Esporín (sporeling.png, 64×64): distribución del creeper vanilla (cabeza, cuerpo, patas) + sombrero de seta abajo.
- Tejedora (weaver.png, 64×64): distribución de la araña vanilla + setas abajo.
- Guardián de micelio (mycelium_guardian.png, 128×128): distribución del gólem de hierro + sombrero y setas.
- *_glow.png: lo que brilla a oscuras (ojos, vetas, motas), transparente en lo demás.

Todo pintado de cero: solo se toma de vanilla la forma de las piezas (client/entity/*Model.java).
UV de caja: para una caja de w×h×d en (u, v), arriba (u+d, v) w×d, abajo (u+d+w, v) w×d, y en la fila v+d:
derecha (u) d×h, delante (u+d) w×h, izquierda (u+d+w) d×h, detrás (u+2d+w) w×h.

Uso: python3 art/entity/make_mob_textures.py [carpeta_de_vistas]
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/stormroot/textures/entity")
CLEAR = (0, 0, 0, 0)


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def ramp(*hexes):
    return [hexc(h) for h in hexes]


def pick(cols, t):
    """Color de una rampa para t en [0, 1] (0 = el primero, el más claro)."""
    return cols[max(0, min(len(cols) - 1, int(t * len(cols))))]


class Noise:
    """Ruido de valor 2D suave y determinista (manchas de varios píxeles, no granos sueltos)."""

    def __init__(self, seed, cell=3):
        self.r = random.Random(seed)
        self.cell = cell
        self.grid = {}

    def at(self, x, y):
        c = self.cell
        gx, gy = x / c, y / c
        x0, y0 = math.floor(gx), math.floor(gy)
        fx, fy = gx - x0, gy - y0

        def g(i, j):
            if (i, j) not in self.grid:
                self.grid[(i, j)] = self.r.random()
            return self.grid[(i, j)]

        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * sx
        b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * sx
        return a + (b - a) * sy


class Tex:
    def __init__(self, w, h, seed):
        self.im = Image.new("RGBA", (w, h), CLEAR)
        self.glow = Image.new("RGBA", (w, h), CLEAR)
        self.r = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.im.putpixel((x, y), c)

    def gl(self, x, y, c):
        """Píxel que brilla: va en la textura normal y en la de brillo."""
        self.px(x, y, c)
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.glow.putpixel((x, y), c)

    @staticmethod
    def faces(u, v, w, h, d):
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }

    def box(self, u, v, w, h, d, painter):
        """Pinta las 6 caras. painter(face, x, y, fw, fh, X, Y) -> color (X, Y = píxel en la textura)."""
        fs = self.faces(u, v, w, h, d)
        for face, (fx, fy, fw, fh) in fs.items():
            for y in range(fh):
                for x in range(fw):
                    self.px(fx + x, fy + y, painter(face, x, y, fw, fh, fx + x, fy + y))
        return fs

    def save(self, name, glow=True):
        self.im.save(os.path.join(OUT, name + ".png"))
        if glow:
            self.glow.save(os.path.join(OUT, name + "_glow.png"))


LIGHT = {"top": 1.0, "front": 0.0, "left": 0.1, "right": 0.15, "back": 0.2, "bottom": 0.55}


def mushroom_painter(t, cap, gills, stem, spot, seed, glow_spots=None):
    """Pinta sombreros (claros arriba con motas, borde más oscuro, láminas debajo) y pies de seta."""
    r = random.Random(seed)

    def cap_p(face, x, y, w, h, X, Y):
        if face == "top":
            if (x * 7 + y * 3 + seed) % 5 == 0 and 0 < x < w - 1 and 0 < y < h - 1:
                if glow_spots is not None and r.random() < 0.6:
                    glow_spots.append((X, Y))
                return spot
            edge = min(x, y, w - 1 - x, h - 1 - y)
            return cap[0] if edge >= 1 else cap[1]
        if face == "bottom":
            return gills[(x + y) % 2] if min(x, y, w - 1 - x, h - 1 - y) > 0 else gills[2]
        return cap[1] if y == 0 else cap[2]

    def stem_p(face, x, y, w, h, X, Y):
        if face == "top":
            return gills[2]
        return stem[0] if (x + y) % 3 else stem[1]

    return cap_p, stem_p


# ---------------------------------------------------------------------------------------------- esporín (creeper)
def sporeling():
    t = Tex(64, 64, 11)
    n = Noise(3, 2)
    skin = ramp("#e9f2c6", "#d6e4ab", "#bfd193", "#a6bb7b", "#8ba264", "#6f854f")

    def body(face, x, y, w, h, X, Y):
        v = n.at(X, Y) * 0.55 + LIGHT[face] * 0.35 + (y / max(1, h - 1)) * 0.25 * (face != "top")
        c = pick(skin, v)
        if face != "top" and face != "bottom" and y == h - 1:
            c = pick(skin, v + 0.18)
        return c

    t.box(0, 0, 8, 8, 8, body)                 # cabeza
    t.box(16, 16, 8, 12, 4, body)              # cuerpo
    leg = ramp("#a6bb7b", "#8ba264", "#6f854f", "#5a6d40")
    t.box(0, 16, 4, 6, 4, lambda f, x, y, w, h, X, Y: pick(leg, y / h + n.at(X, Y) * 0.3 if f != "bottom" else 0.9))

    # Cara (delante de la cabeza, 8×8 en (8, 8)): ojos grandes con brillo, mofletes y boquita.
    fx, fy = 8, 8
    eye, white, blush, mouth = hexc("#1f2a17"), hexc("#ffffff"), hexc("#e9b896"), hexc("#55663a")
    for ex in (1, 5):
        for dx in (0, 1):
            for dy in (2, 3, 4):
                t.px(fx + ex + dx, fy + dy, eye)
        t.px(fx + ex, fy + 2, white)
    t.px(fx + 0, fy + 5, blush)
    t.px(fx + 7, fy + 5, blush)
    for dx in (3, 4):
        t.px(fx + dx, fy + 6, mouth)
    # Motas de espora en el cuerpo, que brillan un poco.
    for (X, Y) in ((22, 23), (25, 26), (20, 30), (27, 22), (35, 25), (38, 29)):
        t.gl(X, Y, hexc("#c9f7a2"))

    cap = ramp("#b28ccc", "#9670b2", "#7a5797", "#5f417a", "#4a3260")
    spot, gill = hexc("#f4ffe0"), ramp("#efdec6", "#d5bf9f", "#b39b78")
    glows = []

    def brim(face, x, y, w, h, X, Y):
        if face == "top":
            if (x * 5 + y * 7) % 11 == 0 and min(x, y, w - 1 - x, h - 1 - y) >= 1:
                glows.append((X, Y))
                return spot
            d = max(abs(x - 5.5), abs(y - 5.5))
            return pick(cap, 0.25 + d / 12 + n.at(X, Y) * 0.25)
        if face == "bottom":
            cx, cy = x - 5.5, y - 5.5
            if abs(cx) < 4.5 and abs(cy) < 4.5 and abs(cx) < 2 and abs(cy) < 2:
                return gill[2]
            return gill[0] if (abs(cx) > abs(cy) and y % 2) or (abs(cx) <= abs(cy) and x % 2) else gill[1]
        return cap[2] if y == 0 else cap[4]

    def dome(face, x, y, w, h, X, Y):
        if face == "top":
            if (x, y) in ((2, 2), (5, 1), (4, 5), (1, 5), (6, 4)):
                glows.append((X, Y))
                return spot
            return pick(cap, abs(x - 3.5) / 8 + abs(y - 3.5) / 8 + n.at(X, Y) * 0.3)
        if face == "bottom":
            return gill[1]
        if (x + 2 * y) % 7 == 0 and y < h - 1:
            glows.append((X, Y))
            return spot
        return pick(cap, 0.2 + y * 0.18 + n.at(X, Y) * 0.2)

    t.box(0, 32, 12, 2, 12, brim)
    t.box(0, 46, 8, 3, 8, dome)
    for (X, Y) in glows:
        t.gl(X, Y, hexc("#e6ffd0"))
    t.save("sporeling")
    return t


# ---------------------------------------------------------------------------------------------- tejedora (araña)
def weaver():
    t = Tex(64, 64, 23)
    n = Noise(5, 2)
    fur = ramp("#6e5e52", "#5a4b40", "#483b33", "#382e28", "#2a221e", "#1e1815")
    thread, thread2 = hexc("#ece6d3"), hexc("#cfc6ad")

    def furp(face, x, y, w, h, X, Y):
        v = n.at(X, Y) * 0.6 + LIGHT[face] * 0.45
        return pick(fur, v)

    t.box(0, 0, 6, 6, 6, furp)                 # body0 (tórax)

    # Abdomen: pelo oscuro cubierto por una red de hilos de micelio claros, con una marca en rombo.
    web = set()
    for k in range(6):
        x0, y0 = t.r.randrange(0, 44), t.r.randrange(12, 32)
        dx, dy = t.r.choice((-1, 1)), t.r.choice((-1, 0, 1))
        for _ in range(t.r.randrange(4, 9)):
            web.add((x0, y0))
            x0 += dx
            y0 += dy if t.r.random() < 0.5 else 0
    diamond = ["....##....",
               "...#oo#...",
               "..#o..o#..",
               "...#oo#...",
               "....##....",
               "..........",
               "...#..#...",
               "....##...."]

    def abdomen(face, x, y, w, h, X, Y):
        if face == "top" and 2 <= y < 2 + len(diamond):
            ch = diamond[y - 2][x]
            if ch == "#":
                return thread
            if ch == "o":
                return hexc("#8f78a6")
        if (X, Y) in web:
            return thread if (X + Y) % 2 else thread2
        c = furp(face, x, y, w, h, X, Y)
        if face == "top" and n.at(X * 2, Y * 2) > 0.78:
            c = hexc("#7a6c8a")  # manchas moradas de moho
        return c

    t.box(0, 12, 10, 8, 12, abdomen)

    def headp(face, x, y, w, h, X, Y):
        c = furp(face, x, y, w, h, X, Y)
        if face == "top" and (x + y * 3) % 7 == 0:
            c = thread2
        return c

    fs = t.box(32, 4, 8, 8, 8, headp)
    fx, fy = fs["front"][0], fs["front"][1]
    # Cara: cuencas oscuras; los ojos verdes van también a la capa de brillo. Quelíceros abajo.
    for x in range(8):
        for y in range(1, 5):
            t.px(fx + x, fy + y, hexc("#16110f"))
    eye, eye2 = hexc("#b8ff6a"), hexc("#6fd13a")
    # Ocho ojos como la araña: dos grandes en el centro, dos medianos a los lados y cuatro pequeños.
    for (x, y) in ((3, 2), (4, 2)):
        t.gl(fx + x, fy + y, hexc("#e4ffb8"))
    for (x, y) in ((3, 3), (4, 3), (1, 2), (6, 2)):
        t.gl(fx + x, fy + y, eye)
    for (x, y) in ((2, 1), (5, 1), (0, 3), (7, 3)):
        t.gl(fx + x, fy + y, eye2)
    fang = hexc("#d9d0b8")
    for (x, y) in ((2, 6), (2, 7), (5, 6), (5, 7)):
        t.px(fx + x, fy + y, fang)

    # Patas (16×2×2): tramos oscuros con articulaciones claras y la punta más oscura.
    def legp(face, x, y, w, h, X, Y):
        if face in ("right", "left"):
            return fur[3]
        if face in ("top", "bottom"):
            seg = x
        else:
            seg = x
        if seg in (5, 6, 11):
            return hexc("#9c8f7d")
        if seg >= 14:
            return fur[5]
        if seg in (2, 9) and y == 0 and face == "top":
            return thread2
        return pick(fur, 0.2 + LIGHT[face] * 0.4 + (0.2 if y == 1 else 0))

    t.box(18, 0, 16, 2, 2, legp)

    cap = ramp("#a58bc0", "#8a6ea6", "#6c528a")
    stem = ramp("#ede4cf", "#d6c9ac")
    gill = ramp("#e9dbc4", "#cbb796", "#b4a07e")
    glows = []
    for size, (cu, su) in enumerate(((0, 20), (28, 40), (44, 52))):
        cw, ch, sw, sh = ((5, 2, 2, 3), (3, 1, 1, 2), (2, 1, 1, 1))[size]
        cap_p, stem_p = mushroom_painter(t, cap, gill, stem, hexc("#f1ffd9"), 7 + size, glows)
        t.box(cu, 32, cw, ch, cw, cap_p)
        t.box(su, 32, sw, sh, sw, stem_p)
    for (X, Y) in glows:
        t.gl(X, Y, hexc("#e6ffd0"))
    t.save("weaver")
    return t


# ---------------------------------------------------------------------------------------------- guardián (gólem)
def guardian():
    t = Tex(128, 128, 47)
    n = Noise(9, 3)
    bark = ramp("#8e6e50", "#7a5d43", "#664c37", "#533d2c", "#412f22", "#302219")
    moss = ramp("#7d9b45", "#64823a", "#4f6a2f")
    vein, vein2 = hexc("#c6ff7a"), hexc("#86c24c")
    grooves = {}

    def barkp(tag):
        def p(face, x, y, w, h, X, Y):
            if face == "top":
                ring = int(max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2)))
                c = bark[1] if ring % 2 else bark[0]
                if n.at(X * 1.5, Y * 1.5) > 0.6:
                    c = pick(moss, n.at(X, Y))
                return c
            if face == "bottom":
                return pick(bark, 0.7 + n.at(X, Y) * 0.3)
            key = (tag, face)
            if key not in grooves:
                grooves[key] = {i for i in range(w) if t.r.random() < 0.3}
            v = n.at(X, Y * 0.5) * 0.45 + LIGHT[face] * 0.4 + (0.3 if x in grooves[key] else 0)
            c = pick(bark, v)
            if y < 2 and n.at(X * 2, Y) > 0.45:
                c = pick(moss, n.at(Y, X))
            return c
        return p

    def vein_line(x0, y0, steps, rng, bounds):
        bx0, by0, bx1, by1 = bounds
        x, y = x0, y0
        for k in range(steps):
            t.gl(x, y, vein if k % 3 else vein2)
            y += 1
            if rng.random() < 0.4:
                x = max(bx0, min(bx1, x + rng.choice((-1, 1))))
            if y > by1:
                break

    # Cabeza (8×10×8 en (0, 0)) y nariz de raíz (2×4×2 en (24, 0)).
    fs = t.box(0, 0, 8, 10, 8, barkp("head"))
    hx, hy = fs["front"][0], fs["front"][1]
    brow = bark[5]
    for x in range(8):
        t.px(hx + x, hy + 3, brow if 1 <= x <= 6 else bark[4])
    for ex in (1, 5):
        t.px(hx + ex, hy + 4, bark[5])
        t.px(hx + ex + 1, hy + 4, bark[5])
        t.gl(hx + ex, hy + 5, vein)
        t.gl(hx + ex + 1, hy + 5, hexc("#f2ffd8"))
    for x in (2, 3, 4, 5):
        t.px(hx + x, hy + 8, bark[5])
    t.gl(hx + 2, hy + 9, vein2)
    t.gl(hx + 5, hy + 9, vein2)
    root = ramp("#b59a76", "#9a7f5d", "#7d6447")
    t.box(24, 0, 2, 4, 2, lambda f, x, y, w, h, X, Y: pick(root, y / 4 + LIGHT[f] * 0.3))

    # Cuerpo (18×12×11 en (0, 40)) con un núcleo de vetas en el pecho, y cintura (9×5×6 en (0, 70)).
    fs = t.box(0, 40, 18, 12, 11, barkp("body"))
    cx, cy = fs["front"][0] + 9, fs["front"][1] + 4
    rng = random.Random(5)
    for (dx, dy) in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
        t.gl(cx + dx, cy + dy, vein if (dx or dy) else hexc("#f6ffe0"))
    t.gl(cx - 1, cy - 1, vein2)
    t.gl(cx + 1, cy + 1, vein2)
    paths = [[(-1, -1), (-2, -2), (-3, -2), (-4, -3), (-5, -3), (-6, -4)],
             [(1, -1), (2, -2), (3, -2), (4, -3), (6, -4), (7, -4)],
             [(-2, 1), (-3, 2), (-4, 3), (-5, 4), (-5, 5), (-6, 6), (-7, 7)],
             [(2, 1), (3, 2), (3, 3), (4, 4), (5, 5), (5, 6), (6, 7)],
             [(0, 2), (0, 3), (-1, 4), (-1, 5), (0, 6), (0, 7)]]
    for p in paths:
        for k, (dx, dy) in enumerate(p):
            t.gl(cx + dx, cy + dy, vein if k < len(p) - 2 else vein2)
    for face in ("right", "left", "back"):
        sx, sy, sw, sh = fs[face]
        vein_line(sx + rng.randrange(2, sw - 2), sy + 1, sh - 2, rng, (sx + 1, sy, sx + sw - 2, sy + sh - 1))
    t.box(0, 70, 9, 5, 6, barkp("waist"))

    # Brazos (4×30×6): corteza que acaba en manos de raíz claras, con una veta que baja.
    root_hand = ramp("#dcc9a5", "#c3ad86", "#a58f6a", "#7c6748")

    def armp(tag):
        base = barkp(tag)

        def p(face, x, y, w, h, X, Y):
            if face == "bottom":
                return pick(root_hand, 0.3 + ((x + y) % 2) * 0.3)
            if face != "top" and y >= h - 5:
                k = y - (h - 5)
                finger = (x % 2 == 0)
                if k == 4:
                    return root_hand[3] if finger else bark[5]
                return pick(root_hand, (0.0 if finger else 0.55) + k * 0.06)
            return base(face, x, y, w, h, X, Y)
        return p

    for tag, (u, v) in (("rarm", (60, 21)), ("larm", (60, 58))):
        fs = t.box(u, v, 4, 30, 6, armp(tag))
        fx, fy, fw, fh = fs["front"]
        vein_line(fx + 1, fy + 3, 18, random.Random(u + v), (fx, fy, fx + fw - 1, fy + 22))

    # Piernas (6×16×5): tocones con raíces oscuras en el pie.
    def legp(tag):
        base = barkp(tag)

        def p(face, x, y, w, h, X, Y):
            if face not in ("top", "bottom") and y >= h - 3:
                return bark[5] if (x + y) % 2 else bark[4]
            return base(face, x, y, w, h, X, Y)
        return p

    t.box(37, 0, 6, 16, 5, legp("rleg"))
    t.box(60, 0, 6, 16, 5, legp("lleg"))

    # Sombrero: ala de 16×2×16 en (0, 90) y cúpula de 10×4×10 en (64, 96), rojizo con motas y láminas.
    cap = ramp("#cf7a55", "#b66244", "#9a4e36", "#7c3d2c", "#5e2e22")
    spot = hexc("#f2e6cd")
    gill = ramp("#e6d4b3", "#c9b08b", "#a38a67")
    sp = set()
    while len(sp) < 12:
        s = (t.r.randrange(1, 15), t.r.randrange(1, 15))
        if all(abs(s[0] - a) + abs(s[1] - b) > 2 for a, b in sp):
            sp.add(s)
    glows = []

    def brim(face, x, y, w, h, X, Y):
        if face == "top":
            if (x, y) in sp:
                if (x + y) % 3 == 0:
                    glows.append((X, Y))
                return spot
            d = max(abs(x - 7.5), abs(y - 7.5))
            return pick(cap, 0.2 + d / 14 + n.at(X, Y) * 0.25)
        if face == "bottom":
            cx_, cy_ = x - 7.5, y - 7.5
            if abs(cx_) < 4.5 and abs(cy_) < 4.5:
                return gill[2]
            return gill[0] if ((abs(cx_) > abs(cy_)) and y % 2) or ((abs(cx_) <= abs(cy_)) and x % 2) else gill[1]
        return spot if (y == 0 and (x * 3) % 7 == 0) else (cap[1] if y == 0 else cap[4])

    def dome(face, x, y, w, h, X, Y):
        if face == "top":
            if (x * 3 + y * 5) % 9 == 0 and 0 < x < w - 1 and 0 < y < h - 1:
                glows.append((X, Y))
                return spot
            return pick(cap, abs(x - 4.5) / 12 + abs(y - 4.5) / 12 + n.at(X, Y) * 0.25)
        if face == "bottom":
            return gill[2]
        if (x * 2 + y * 3) % 7 == 0 and y < h - 1:
            return spot
        return pick(cap, 0.15 + y * 0.12 + n.at(X, Y) * 0.2)

    t.box(0, 90, 16, 2, 16, brim)
    t.box(64, 96, 10, 4, 10, dome)
    stem = ramp("#e9dcc0", "#cbb894")
    for (cu, cv, cw, ch, su, sv, sw, sh, seed) in ((84, 0, 6, 2, 108, 0, 2, 3, 3), (84, 10, 4, 1, 100, 10, 1, 2, 4)):
        cap_p, stem_p = mushroom_painter(t, cap[:1] + cap[1:3], gill, stem, spot, seed, glows)
        t.box(cu, cv, cw, ch, cw, cap_p)
        t.box(su, sv, sw, sh, sw, stem_p)
    for (X, Y) in glows:
        t.gl(X, Y, hexc("#ffe7b8"))
    t.save("mycelium_guardian")
    return t


def preview(texs, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for name, t, s in texs:
        for suffix, im in (("", t.im), ("_glow", t.glow)):
            bg = Image.new("RGBA", im.size, (255, 255, 255, 255) if not suffix else (24, 24, 30, 255))
            if not suffix:
                for y in range(im.height):
                    for x in range(im.width):
                        if (x + y) % 2:
                            bg.putpixel((x, y), (232, 232, 232, 255))
            bg.alpha_composite(im)
            bg.resize((im.width * s, im.height * s), Image.NEAREST).save(os.path.join(out_dir, name + suffix + ".png"))


if __name__ == "__main__":
    texs = [("sporeling", sporeling(), 8), ("weaver", weaver(), 8), ("mycelium_guardian", guardian(), 5)]
    if len(sys.argv) > 1:
        preview(texs, sys.argv[1])
