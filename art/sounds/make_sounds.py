# Sonidos del clima de Stormroot a partir de grabaciones CC0 de OpenGameArt (ver docs/creditos-sonidos.md).
# Entradas: los archivos de la tabla de créditos pasados a crudo f32le mono 44100 Hz, en out/:
#   ffmpeg -i "wind woosh loop.ogg" -ac 1 -ar 44100 -f f32le out/wind_20woosh_20loop.raw   (y así con cada uno)
# Salida: final/<nombre>.ogg → assets/stormroot/sounds/weather/.
import numpy as np, subprocess, os

SR = 44100

def load(name):
    return np.fromfile(f"out/{name}.raw", dtype=np.float32)

def save(name, x, q="3"):
    x = x / max(1e-9, np.max(np.abs(x))) * 0.85
    x.astype(np.float32).tofile("/tmp/x.raw")
    os.makedirs("final", exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "/tmp/x.raw",
                    "-c:a", "libvorbis", "-q:a", q, f"final/{name}.ogg"], check=True)
    print(name, round(len(x) / SR, 1), "s")

def loopify(x, fade=1.0):
    """Bucle sin costura: el final se funde sobre el principio."""
    n = int(fade * SR)
    w = np.linspace(0, 1, n, dtype=np.float32)
    out = x[:-n].copy()
    out[:n] = x[:n] * w + x[-n:] * (1 - w)
    return out

def stretch(x, rate):
    """Cambia duración y tono a la vez (remuestreo simple)."""
    idx = np.arange(0, len(x) - 1, rate)
    return np.interp(idx, np.arange(len(x)), x).astype(np.float32)

def lp(x, alpha):
    """Paso bajo de un polo (alpha pequeño = más grave)."""
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y

def hp(x, alpha):
    return x - lp(x, alpha)

def tremolo(x, hz, depth):
    t = np.arange(len(x)) / SR
    return x * (1 - depth + depth * 0.5 * (1 + np.sin(2 * np.pi * hz * t))).astype(np.float32)

rng = np.random.default_rng(7)
wind = load("wind_20woosh_20loop")  # bucle de 6 s, CC0 (SketchMan3)
space = load("space-wind")          # drone de 48 s, CC0
ice = load("ice")                   # golpe de hielo, CC0 (bart)
snap = load("coldsnap")

# Base común: dos pasadas del bucle de viento, la segunda un pelo más grave (12 s sin repetición evidente).
base = np.concatenate([wind, stretch(wind, 0.97)[:len(wind)]])

# 1. viento (lluvia/monzón): la base suavizada.
save("wind", loopify(lp(base, 0.25), 0.8))

# 2. viento fuerte (tormenta): más grave, dos capas con estiramientos distintos y rachas lentas.
deep = stretch(base, 0.82)
layer = stretch(base, 0.805)[:len(deep)]
save("wind_strong", loopify(tremolo(deep + 0.7 * layer, 0.13, 0.35), 1.0))

# 3. ventisca: silbido = ruido en banda media que "aúlla" despacio, sobre un colchón de viento; agudos recortados.
bed = stretch(base, 1.15)
n = len(bed)
band = lp(hp(rng.standard_normal(n).astype(np.float32), 0.02), 0.18)
t = np.arange(n) / SR
howl = band * (0.4 + 0.6 * 0.5 * (1 + np.sin(2 * np.pi * 0.21 * t + np.sin(2 * np.pi * 0.07 * t)))).astype(np.float32)
save("blizzard", lp(lp(loopify((1.1 * howl + 0.9 * bed).astype(np.float32), 0.8), 0.5), 0.5))

# 4. tormenta de arena: siseo (paso alto fuerte) con parpadeo rápido sobre un colchón grave.
hiss = hp(stretch(base, 1.1), 0.35)
sand = 0.9 * tremolo(hiss, 0.8, 0.3) + 0.5 * stretch(base, 0.9)[:len(hiss)]
save("sandstorm", loopify(sand.astype(np.float32), 0.8))

# 5. aurora: coro de senos desafinados y lentos + la textura del drone muy al fondo.
dur = 20 * SR
t = np.arange(dur) / SR
pad = np.zeros(dur, dtype=np.float32)
for f0, a, v in ((110, 0.5, 0.05), (165, 0.35, 0.07), (220, 0.3, 0.045), (275, 0.18, 0.09), (330, 0.12, 0.06)):
    pad += (a * np.sin(2 * np.pi * f0 * t + 2.0 * np.sin(2 * np.pi * v * t))).astype(np.float32)
pad = pad * (0.6 + 0.4 * 0.5 * (1 + np.sin(2 * np.pi * 0.05 * t))).astype(np.float32)
tex = lp(space[int(6 * SR):int(6 * SR) + dur], 0.10)
tex = tex / max(1e-9, np.max(np.abs(tex)))
save("aurora", loopify((0.75 * pad / np.max(np.abs(pad)) + 0.25 * tex).astype(np.float32), 3.0), q="2")

# 6. granizo: golpecitos de hielo con tonos al azar espolvoreados sobre un lecho tenue de viento.
dur = 9 * SR
hailx = 0.18 * stretch(base, 1.05)[:dur]
hits = [stretch(ice, r) for r in (0.8, 1.0, 1.3, 1.7, 2.2)] + [stretch(snap[:int(0.5 * SR)], r) for r in (1.2, 1.8)]
pos = 0
while pos < dur - SR:
    h = hits[rng.integers(len(hits))] * (0.25 + 0.5 * rng.random())
    end = min(dur, pos + len(h))
    hailx[pos:end] += h[:end - pos]
    pos += int(SR * (0.08 + 0.3 * rng.random()))
save("hail", loopify(hailx, 0.5))
