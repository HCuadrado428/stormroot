package io.github.hcuadrado428.stormroot.weather;

/**
 * Tipos de frente climático. Java puro: sin clases de Minecraft ni del loader.
 *
 * <p>Los valores son provisionales; más adelante saldrán de JSON (datapacks) para que
 * los modpacks puedan ajustarlos.
 */
public enum FrontType {
    //            id               radio min/max (bloques)  velocidad (bloques/s)  duración (min/max, segundos)
    RAIN("rain", 96, 192, 0.6, 600, 1500),
    THUNDERSTORM("thunderstorm", 64, 128, 1.0, 300, 900),
    FOG("fog", 80, 160, 0.3, 450, 1200),
    /** Solo nace en biomas fríos y cumbres: nieve que se acumula, poca visibilidad y cuevas que se hielan. */
    BLIZZARD("blizzard", 96, 160, 0.8, 600, 1200),
    /** Ola de calor: enorme, larga y casi quieta. Despeja el cielo, seca el suelo y derrite las cuevas heladas. */
    HEAT_WAVE("heat_wave", 192, 320, 0.15, 1200, 2400),
    /** Monzón: lluvia torrencial enorme y larga en junglas, pantanos y costas cálidas; inunda cuevas y orillas. */
    MONSOON("monsoon", 160, 256, 0.4, 1500, 2400);

    private final String id;
    private final int minRadius;
    private final int maxRadius;
    private final double speed;
    private final int minDurationSeconds;
    private final int maxDurationSeconds;

    FrontType(String id, int minRadius, int maxRadius, double speed, int minDurationSeconds, int maxDurationSeconds) {
        this.id = id;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.speed = speed;
        this.minDurationSeconds = minDurationSeconds;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public String id() {
        return id;
    }

    public int minRadius() {
        return minRadius;
    }

    public int maxRadius() {
        return maxRadius;
    }

    /** Velocidad base en bloques por segundo. */
    public double speed() {
        return speed;
    }

    public int minDurationSeconds() {
        return minDurationSeconds;
    }

    public int maxDurationSeconds() {
        return maxDurationSeconds;
    }

    /** {@code true} si trae precipitación (lluvia o nieve según el bioma). */
    public boolean precipitates() {
        return this == RAIN || this == THUNDERSTORM || this == BLIZZARD || this == MONSOON;
    }

    public static FrontType byId(String id) {
        for (FrontType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
