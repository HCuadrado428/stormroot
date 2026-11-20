"""Genera las texturas de los mobs de Stormroot y sus capas de brillo.

- Esporín (sporeling.png, 32×32): cuerpo cúbico con sombrero de seta (ala y cúpula) y cuatro patitas.
- Tejedora (weaver.png, 64×64): distribución de la araña vanilla + setas abajo.
- Guardián de micelio (mycelium_guardian.png, 128×128): una seta gigante que anda (pie con cara, sombrero, raíces).
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


# ---------------------------------------------------------------------------------------------- esporín
def sporeling():
    """32×32: cuerpo 6×6×6 en (0, 0), ala del sombrero 8×2×8 en (0, 12), cúpula 6×2×6 en (8, 22), patas 1×2×1 en (0, 22)."""
    t = Tex(32, 32, 11)
    n = Noise(3, 2)
    skin = ramp("#eef6cf", "#dde9b6", "#c8d99c", "#b0c483", "#95ab6a", "#7a8f54")

    def body(face, x, y, w, h, X, Y):
        v = n.at(X, Y) * 0.4 + LIGHT[face] * 0.35 + (y / max(1, h - 1)) * 0.3 * (face not in ("top", "bottom"))
        c = pick(skin, v)
        if face in ("left", "right", "back") and (x * 5 + y * 3) % 11 == 0 and 0 < y < h - 1:
            t.glow.putpixel((X, Y), hexc("#d8ffb4"))
            return hexc("#f4ffe2")
        return c

    fs = t.box(0, 0, 6, 6, 6, body)
    fx, fy = fs["front"][0], fs["front"][1]
    eye, white, blush, mouth = hexc("#1f2a17"), hexc("#ffffff"), hexc("#efb592"), hexc("#8a9f62")
    for ex in (1, 4):
        t.px(fx + ex, fy + 1, eye)
        t.px(fx + ex, fy + 2, eye)
        t.px(fx + ex + 1, fy + 2, eye)
        t.px(fx + ex + 1, fy + 1, white)
    t.px(fx + 0, fy + 3, blush)
    t.px(fx + 5, fy + 3, blush)
    t.px(fx + 2, fy + 4, mouth)
    t.px(fx + 3, fy + 4, mouth)

    cap = ramp("#c29ddb", "#a682c2", "#8a66a7", "#6f4f8b", "#573c70", "#432d58")
    spot, gill = hexc("#f6ffe4"), ramp("#efdec6", "#d8c2a2", "#b9a07c")
    glows = []

    def brim(face, x, y, w, h, X, Y):
        if face == "top":
            # El centro lo tapa la cúpula: aquí solo se ve el borde del ala.
            if (x, y) in ((0, 3), (7, 5), (2, 7), (5, 0), (7, 1), (0, 6)):
                glows.append((X, Y))
                return spot
            return pick(cap, 0.3 + n.at(X, Y) * 0.35)
        if face == "bottom":
            cx, cy = x - 3.5, y - 3.5
            if abs(cx) < 1.5 and abs(cy) < 1.5:
                return gill[2]
            return gill[0] if ((abs(cx) > abs(cy)) and y % 2) or ((abs(cx) <= abs(cy)) and x % 2) else gill[1]
        if y == 0:
            return spot if (x * 3 + (face == "front")) % 5 == 0 else cap[2]
        return cap[4]

    def dome(face, x, y, w, h, X, Y):
        if face == "top":
            if (x, y) in ((1, 1), (4, 1), (2, 3), (5, 4), (1, 5)):
                glows.append((X, Y))
                return spot
            return pick(cap, abs(x - 2.5) / 9 + abs(y - 2.5) / 9 + n.at(X, Y) * 0.3)
        if face == "bottom":
            return gill[1]
        if (x * 2 + y + (face == "left")) % 5 == 0 and y == 0:
            glows.append((X, Y))
            return spot
        return pick(cap, 0.15 + y * 0.25 + n.at(X, Y) * 0.2)

    t.box(0, 12, 8, 2, 8, brim)
    t.box(8, 22, 6, 2, 6, dome)
    leg = ramp("#b0c483", "#95ab6a", "#7a8f54", "#5f7240")
    t.box(0, 22, 1, 2, 1, lambda f, x, y, w, h, X, Y: leg[min(3, y + (1 if f in ("right", "back") else 0))] if f != "bottom" else leg[3])
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


# ---------------------------------------------------------------------------------------------- guardián (seta gigante)
def guardian():
    """128×128: sombrero de tres pisos (0,0)/(0,30)/(80,30), pie (0,54), anillo (44,54), brazo (96,54) con dedos
    (112,54)/(112,60), pata (0,82) y seta pequeña (20,82)/(28,82)."""
    t = Tex(128, 128, 47)
    n = Noise(9, 3)
    cap = ramp("#d8805a", "#c46a48", "#ab573a", "#8f4630", "#733727", "#57291e")
    spot, spot2 = hexc("#f6ecd6"), hexc("#e3d2b2")
    gill = ramp("#eadbc0", "#d4c09f", "#b9a27f", "#8f7a5b")
    stem = ramp("#f1e8d4", "#e2d6bb", "#cfc0a0", "#b7a684", "#9a8a6a")
    root = ramp("#a88a64", "#8f744f", "#765e3f", "#5e4a31", "#463724")
    moss = ramp("#8fae55", "#76964a")
    gill_glow = hexc("#6fa04a")

    def spots(w, h, count, seed):
        r = random.Random(seed)
        out = set()
        tries = 0
        while len(out) < count * 3 and tries < 500:
            tries += 1
            x, y = r.randrange(1, w - 2), r.randrange(1, h - 2)
            cells = [(x, y), (x + 1, y), (x, y + 1)] if r.random() < 0.6 else [(x, y)]
            if all(abs(cx - a) > 1 or abs(cy - b) > 1 for a, b in out for cx, cy in cells if (a, b) not in cells):
                out.update(cells)
        return out

    def cap_tier(tier, seed):
        sp = spots(*{0: (26, 26), 1: (20, 20), 2: (12, 12)}[tier], {0: 22, 1: 14, 2: 6}[tier], seed)

        def p(face, x, y, w, h, X, Y):
            if face == "top":
                if (x, y) in sp:
                    return spot if (x + y) % 3 else spot2
                d = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2)) / (w / 2)
                return pick(cap, 0.05 + d * 0.45 + n.at(X, Y) * 0.3 + tier * -0.05)
            if face == "bottom":
                cx, cy = x - (w - 1) / 2, y - (h - 1) / 2
                if tier == 0 and abs(cx) < 6 and abs(cy) < 5:
                    return stem[3]
                # láminas en radios; algunas brillan tenues
                radial = (abs(cx) > abs(cy) and y % 2 == 0) or (abs(cx) <= abs(cy) and x % 2 == 0)
                if radial and tier == 0 and (x * 3 + y) % 7 == 0:
                    t.glow.putpixel((X, Y), gill_glow)
                edge = min(x, y, w - 1 - x, h - 1 - y)
                return gill[0] if radial else (gill[1] if edge > 1 else gill[2])
            # borde: claro arriba con motas, se oscurece hacia abajo
            if y == 0:
                return spot if (x * 5 + tier) % 7 == 0 else cap[1]
            return pick(cap, 0.35 + y / h * 0.6 + n.at(X, Y) * 0.15)
        return p

    t.box(0, 0, 26, 4, 26, cap_tier(0, 1))
    t.box(0, 30, 20, 4, 20, cap_tier(1, 2))
    t.box(80, 30, 12, 3, 12, cap_tier(2, 3))

    def stem_p(face, x, y, w, h, X, Y):
        if face == "top":
            return stem[2]
        if face == "bottom":
            return stem[4]
        fiber = (x * 7 + 3) % 5 == 0
        v = LIGHT[face] * 0.3 + n.at(X, Y * 0.4) * 0.3 + (0.2 if fiber else 0) + (y / h) * 0.35
        c = pick(stem, v)
        if y >= h - 3 and n.at(X * 2, Y * 2) > 0.45:
            c = pick(moss, n.at(Y, X))
        return c

    fs = t.box(0, 54, 12, 16, 10, stem_p)
    fx, fy = fs["front"][0], fs["front"][1]
    # Cara: ceño, ojos hundidos que brillan (2×2) y una boca como una grieta.
    for x in range(1, 11):
        t.px(fx + x, fy + 3, stem[3])
    for ex in (2, 7):
        for dx in (-1, 0, 1, 2):
            t.px(fx + ex + dx, fy + 4, stem[4])
            t.px(fx + ex + dx, fy + 7, stem[3])
        for dy in (5, 6):
            t.px(fx + ex - 1, fy + dy, stem[4])
            t.px(fx + ex + 2, fy + dy, stem[4])
        t.gl(fx + ex, fy + 5, hexc("#f2ffd8"))
        t.gl(fx + ex + 1, fy + 5, hexc("#c8ff7c"))
        t.gl(fx + ex, fy + 6, hexc("#c8ff7c"))
        t.gl(fx + ex + 1, fy + 6, hexc("#8fd450"))
    for (x, y) in ((4, 11), (5, 11), (6, 11), (7, 11), (3, 10), (8, 10), (5, 12), (6, 12)):
        t.px(fx + x, fy + y, stem[4] if y == 11 else stem[3])

    def ring_p(face, x, y, w, h, X, Y):
        if face == "top":
            return stem[1] if (x + y) % 4 else stem[2]
        if face == "bottom":
            return stem[3]
        return stem[0] if y == 0 else (stem[2] if x % 3 else stem[3])

    t.box(44, 54, 14, 2, 12, ring_p)

    def root_p(tip):
        def p(face, x, y, w, h, X, Y):
            if face in ("top", "bottom"):
                return root[2] if face == "top" else root[4]
            v = LIGHT[face] * 0.3 + n.at(X * 1.3, Y * 0.5) * 0.35 + (0.25 if (x + X) % 3 == 0 else 0)
            c = pick(root, v + (y / h) * 0.25)
            if tip and y >= h - 1:
                c = root[4]
            return c
        return p

    t.box(96, 54, 4, 13, 4, root_p(False))
    t.box(112, 54, 2, 4, 2, root_p(True))
    t.box(112, 60, 2, 3, 2, root_p(True))

    def leg_p(face, x, y, w, h, X, Y):
        c = root_p(False)(face, x, y, w, h, X, Y)
        if face not in ("top", "bottom") and y >= h - 2:
            c = root[4] if (x + y) % 2 else root[3]
        return c

    t.box(0, 82, 5, 6, 5, leg_p)
    cap_p, stem_small = mushroom_painter(t, cap[1:4], gill, stem[:2], spot, 5)
    t.box(28, 82, 6, 2, 6, cap_p)
    t.box(20, 82, 2, 3, 2, stem_small)
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
    texs = [("sporeling", sporeling(), 12), ("weaver", weaver(), 8), ("mycelium_guardian", guardian(), 5)]
    if len(sys.argv) > 1:
        preview(texs, sys.argv[1])
