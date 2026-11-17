package io.github.hcuadrado428.stormroot.weather;

/**
 * Grupo de biomas para decidir qué frentes nacen dónde (tabla "Dónde puede aparecer cada uno" del catálogo de
 * fenómenos). Java puro: {@code FrontTicker} clasifica el bioma y {@link FrontSimulation} elige el tipo.
 */
public enum ClimateZone {
    COLD(1.0, 0.5, 0.7),
    TEMPERATE(1.0, 1.0, 0.7),
    HUMID(2.0, 2.0, 2.0),
    /** Sin lluvia en vanilla (desierto, badlands, sabana): solo tormentas, que ahí son tormentas secas. */
    DRY(0.0, 0.6, 0.0),
    OCEAN(2.0, 1.0, 2.0),
    MOUNTAIN(1.0, 1.0, 0.7);

    private final double rain;
    private final double thunderstorm;
    private final double fog;

    ClimateZone(double rain, double thunderstorm, double fog) {
        this.rain = rain;
        this.thunderstorm = thunderstorm;
        this.fog = fog;
    }

    /** Peso de un tipo de frente en esta zona (0 = nunca nace aquí). */
    public double weight(FrontType type) {
        return switch (type) {
            case RAIN -> rain;
            case THUNDERSTORM -> thunderstorm;
            case FOG -> fog;
        };
    }

    /** Clasificador de posiciones (lo da la capa de Minecraft). */
    @FunctionalInterface
    public interface Lookup {
        ClimateZone at(double x, double z);
    }
}
