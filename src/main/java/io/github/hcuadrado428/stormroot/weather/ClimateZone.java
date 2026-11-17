package io.github.hcuadrado428.stormroot.weather;

/**
 * Grupo de biomas para decidir qué frentes nacen dónde (tabla "Dónde puede aparecer cada uno" del catálogo de
 * fenómenos). Java puro: {@code FrontTicker} clasifica el bioma y {@link FrontSimulation} elige el tipo.
 */
public enum ClimateZone {
    //        lluvia tormenta niebla ventisca calor monzón
    COLD(1.0, 0.5, 0.7, 1.6, 0.0, 0.0),
    TEMPERATE(1.0, 1.0, 0.7, 0.0, 0.4, 0.0),
    HUMID(2.0, 2.0, 2.0, 0.0, 0.3, 1.2),
    /** Sin lluvia en vanilla (desierto, badlands, sabana): tormentas (secas) y olas de calor. */
    DRY(0.0, 0.6, 0.0, 0.0, 0.8, 0.0),
    OCEAN(2.0, 1.0, 2.0, 0.0, 0.0, 0.6),
    MOUNTAIN(1.0, 1.0, 0.7, 0.6, 0.0, 0.0);

    private final double rain;
    private final double thunderstorm;
    private final double fog;
    private final double blizzard;
    private final double heatWave;
    private final double monsoon;

    ClimateZone(double rain, double thunderstorm, double fog, double blizzard, double heatWave, double monsoon) {
        this.rain = rain;
        this.thunderstorm = thunderstorm;
        this.fog = fog;
        this.blizzard = blizzard;
        this.heatWave = heatWave;
        this.monsoon = monsoon;
    }

    /** Peso de un tipo de frente en esta zona (0 = nunca nace aquí). */
    public double weight(FrontType type) {
        return switch (type) {
            case RAIN -> rain;
            case THUNDERSTORM -> thunderstorm;
            case FOG -> fog;
            case BLIZZARD -> blizzard;
            case HEAT_WAVE -> heatWave;
            case MONSOON -> monsoon;
        };
    }

    /** Clasificador de posiciones (lo da la capa de Minecraft). */
    @FunctionalInterface
    public interface Lookup {
        ClimateZone at(double x, double z);
    }
}
