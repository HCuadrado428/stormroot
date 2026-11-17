package io.github.hcuadrado428.stormroot.sky;

/**
 * Un arcoíris: tras una lluvia o tormenta, de día. Uno de sus extremos toca el suelo en ({@code x}, {@code y}, {@code z})
 * y el arco se levanta hacia {@code angle} (radianes). Dura hasta el tick de juego {@code endTick}. Java puro.
 */
public record Rainbow(long id, double x, double y, double z, double angle, long startTick, long endTick) {
    /** Radio del arco en bloques. */
    public static final double RADIUS = 70;
    /** Lo que dura (2,5 minutos). */
    public static final long DURATION_TICKS = 3000;

    /** 0–1: aparece y se desvanece en los primeros y últimos 20 segundos. */
    public double fade(long gameTime) {
        double in = (gameTime - startTick) / 400.0;
        double out = (endTick - gameTime) / 400.0;
        return Math.max(0, Math.min(1, Math.min(in, out)));
    }
}
