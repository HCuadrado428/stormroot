package io.github.hcuadrado428.stormroot.sky;

/**
 * Ruido de valores 3D suave y determinista (misma semilla y coordenadas = mismo valor en cualquier máquina).
 * Java puro: lo usan el cliente (para dibujar) y el servidor (para saber dónde hay nube).
 */
public final class ValueNoise {
    private ValueNoise() {
    }

    /** Valor en [0, 1] para (x, y, z), interpolado suavemente entre los puntos de la rejilla entera. */
    public static double sample(long seed, double x, double y, double z) {
        long x0 = (long) Math.floor(x);
        long y0 = (long) Math.floor(y);
        long z0 = (long) Math.floor(z);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double fz = smooth(z - z0);
        double c000 = hash(seed, x0, y0, z0);
        double c100 = hash(seed, x0 + 1, y0, z0);
        double c010 = hash(seed, x0, y0 + 1, z0);
        double c110 = hash(seed, x0 + 1, y0 + 1, z0);
        double c001 = hash(seed, x0, y0, z0 + 1);
        double c101 = hash(seed, x0 + 1, y0, z0 + 1);
        double c011 = hash(seed, x0, y0 + 1, z0 + 1);
        double c111 = hash(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(c000, c100, fx);
        double x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx);
        double x11 = lerp(c011, c111, fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    /** Suma de octavas (más detalle en los bordes), normalizada a [0, 1]. */
    public static double fbm(long seed, double x, double y, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double freq = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * sample(seed + i * 0x9E3779B97F4A7C15L, x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    private static double hash(long seed, long x, long y, long z) {
        long h = seed ^ (x * 0x632BE59BD9B4E019L) ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0x85157AF5L);
        h = (h ^ (h >>> 31)) * 0x7FB5D329728EA185L;
        h = (h ^ (h >>> 27)) * 0x81DADEF4BC2DD44DL;
        h = h ^ (h >>> 33);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
