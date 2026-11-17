package io.github.hcuadrado428.stormroot.seep;

/**
 * Velocidades de la filtración (por segundo de juego). Java puro.
 *
 * <p>Con los valores por defecto: unos 4 minutos de lluvia fuerte empapan el suelo (0 → 0,6); se seca en unos
 * 20 minutos sin lluvia; tras otros ~40 minutos secos empieza la sequía; el subsuelo va unos 10 minutos por detrás.
 */
public record SeepageRates(double wetPerSecond, double dryPerSecond, double droughtPerSecond, double deepLagSeconds) {
    public static final SeepageRates DEFAULT = new SeepageRates(0.0025, 0.0008, 0.0004, 600);

    /** Mismas proporciones, todo {@code factor} veces más rápido (config). */
    public SeepageRates scaled(double factor) {
        double f = Math.max(0.01, factor);
        return new SeepageRates(wetPerSecond * f, dryPerSecond * f, droughtPerSecond * f, deepLagSeconds / f);
    }
}
