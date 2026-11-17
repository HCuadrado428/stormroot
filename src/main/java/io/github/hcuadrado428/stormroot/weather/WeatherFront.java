package io.github.hcuadrado428.stormroot.weather;

/**
 * Un frente climático: un círculo que se desplaza por el mundo y caduca.
 * Java puro (sin Minecraft) para poder probarlo y reutilizarlo en todas las versiones.
 */
public final class WeatherFront {
    private final long id;
    private final FrontType type;
    private double x;
    private double z;
    private final double radius;
    /** Velocidad en bloques por segundo en cada eje. */
    private final double vx;
    private final double vz;
    private double ageSeconds;
    private final double maxAgeSeconds;

    public WeatherFront(long id, FrontType type, double x, double z, double radius,
                        double vx, double vz, double ageSeconds, double maxAgeSeconds) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.z = z;
        this.radius = radius;
        this.vx = vx;
        this.vz = vz;
        this.ageSeconds = ageSeconds;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    /** Copia independiente (para no compartir objetos entre servidor y cliente en un solo jugador). */
    public WeatherFront copy() {
        return new WeatherFront(id, type, x, z, radius, vx, vz, ageSeconds, maxAgeSeconds);
    }

    /** Avanza el frente {@code seconds} segundos. */
    public void advance(double seconds) {
        x += vx * seconds;
        z += vz * seconds;
        ageSeconds += seconds;
    }

    public boolean isExpired() {
        return ageSeconds >= maxAgeSeconds;
    }

    public boolean contains(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Intensidad del frente en un punto, de 0 (fuera) a 1 (centro).
     * También se debilita al nacer y al morir para que no aparezca ni desaparezca de golpe.
     */
    public double intensityAt(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= radius) {
            return 0.0;
        }
        double spatial = 1.0 - dist / radius;
        return spatial * lifeFactor();
    }

    /** 0→1 en el primer 10 % de su vida, 1 en el medio y 1→0 en el último 10 %. */
    public double lifeFactor() {
        double t = ageSeconds / maxAgeSeconds;
        if (t < 0.1) {
            return t / 0.1;
        }
        if (t > 0.9) {
            return Math.max(0.0, (1.0 - t) / 0.1);
        }
        return 1.0;
    }

    public boolean overlaps(WeatherFront other) {
        double dx = other.x - x;
        double dz = other.z - z;
        double r = radius + other.radius;
        return dx * dx + dz * dz <= r * r;
    }

    public long id() {
        return id;
    }

    public FrontType type() {
        return type;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public double radius() {
        return radius;
    }

    public double vx() {
        return vx;
    }

    public double vz() {
        return vz;
    }

    public double ageSeconds() {
        return ageSeconds;
    }

    public double maxAgeSeconds() {
        return maxAgeSeconds;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Front#%d[%s at %.0f,%.0f r=%.0f age=%.0f/%.0fs]",
                id, type.id(), x, z, radius, ageSeconds, maxAgeSeconds);
    }
}
