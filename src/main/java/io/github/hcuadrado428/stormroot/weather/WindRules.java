package io.github.hcuadrado428.stormroot.weather;

import java.util.List;

/**
 * Viento (Java puro): fuerza en cada punto y empuje sobre los proyectiles. El servidor y el cliente hacen el mismo
 * cálculo con el mismo viento (va en el paquete), así que las flechas no dan tirones.
 *
 * <p>Fuerza de 0 a 1: {@link #BASE} con buen tiempo; hasta 1 dentro de las tormentas y en la franja que llevan delante
 * (hasta {@link #AHEAD} veces su radio). La exposición (techo, bosque, altura, terreno abierto) la pone quien llama.
 *
 * <p>Para que el PvP no cambie de cerca, los proyectiles no notan el viento durante sus primeros {@link #GRACE_TICKS}
 * ticks (unos 15–20 bloques con el arco tenso). Con fuerza 1, una flecha a 40 bloques se desvía como un bloque.
 */
public final class WindRules {
    public static final double BASE = 0.25;
    /** Las tormentas empujan el viento hasta 1,5 veces su radio. */
    public static final double AHEAD = 1.5;
    public static final int GRACE_TICKS = 6;
    /** Aceleración (bloques/tick²) sobre una flecha con fuerza 1. */
    public static final double ARROW_ACCEL = 0.025;
    /** Por debajo de esta velocidad (bloques/tick) el proyectil está quieto o clavado: no se empuja. */
    public static final double MIN_SPEED = 0.1;

    /** Cuánto le afecta el viento a cada tipo de proyectil (1 = flecha). */
    public enum Projectile {
        ARROW(1.0), TRIDENT(0.5), LIGHT(2.0), ENDER_PEARL(0.1), THROWN(1.0), NONE(0.0);

        public final double factor;

        Projectile(double factor) {
            this.factor = factor;
        }
    }

    private WindRules() {
    }

    /** Fuerza del viento en (x, z) sin contar el resguardo. */
    public static double strength(List<WeatherFront> fronts, double x, double z) {
        double best = BASE;
        for (WeatherFront front : fronts) {
            double gust = gust(front.type());
            if (gust <= 0) {
                continue;
            }
            double dx = x - front.x();
            double dz = z - front.z();
            double d = Math.sqrt(dx * dx + dz * dz);
            double r = front.radius();
            double reach;
            if (d <= r) {
                reach = 1;
            } else if (d < r * AHEAD) {
                reach = 1 - (d - r) / (r * (AHEAD - 1));
            } else {
                continue;
            }
            // El frente sopla más cuanto más maduro está (como su intensidad en el centro).
            double life = front.lifeFactor();
            best = Math.max(best, BASE + (gust - BASE) * reach * life);
        }
        return Math.min(1, best);
    }

    /** Fuerza máxima que da cada tipo de frente. */
    public static double gust(FrontType type) {
        return switch (type) {
            case THUNDERSTORM, BLIZZARD -> 1.0;
            case MONSOON -> 0.8;
            case RAIN -> 0.45;
            default -> 0;
        };
    }

    /**
     * Resguardo: 1 a cielo abierto, 0,5 bajo los árboles, 0 bajo techo o en cuevas. Más en terreno abierto y en lo alto
     * (hasta ×1,5 a 80 bloques sobre el mar).
     */
    public static double exposure(boolean openSky, int skyLight, boolean openTerrain, double heightAboveSea) {
        double shelter = openSky ? 1.0 : skyLight >= 10 ? 0.5 : 0.0;
        double terrain = openTerrain ? 1.2 : 1.0;
        double height = 1 + Math.max(0, Math.min(0.5, (heightAboveSea - 20) / 120));
        return shelter * terrain * height;
    }

    /** Aceleración del viento sobre un proyectil, en bloques/tick², o 0 si aún está en su gracia o parado. */
    public static double projectileAccel(Projectile kind, int ticksFlying, double speed, double windHere) {
        if (kind.factor <= 0 || ticksFlying < GRACE_TICKS || speed < MIN_SPEED) {
            return 0;
        }
        return ARROW_ACCEL * kind.factor * Math.min(1.5, windHere);
    }

    /** Dirección del viento (hacia dónde sopla) como vector unitario en x. */
    public static double dirX(double windAngle) {
        return Math.cos(windAngle);
    }

    public static double dirZ(double windAngle) {
        return Math.sin(windAngle);
    }

    /** Empuje a las elytras y los barcos: un poco a favor o en contra según hacia dónde vas. */
    public static double travelAccel(double windHere) {
        return 0.004 * windHere;
    }
}
