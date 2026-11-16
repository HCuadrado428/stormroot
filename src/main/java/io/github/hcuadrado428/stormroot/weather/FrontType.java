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
    FOG("fog", 80, 160, 0.3, 450, 1200);

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

    public static FrontType byId(String id) {
        for (FrontType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
