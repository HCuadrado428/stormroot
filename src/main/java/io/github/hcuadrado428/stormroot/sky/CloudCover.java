package io.github.hcuadrado428.stormroot.sky;

import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.List;

/**
 * El cielo de Stormroot: qué nube hay en cada punto. Java puro y determinista: con la misma semilla, el mismo tiempo de
 * juego, el mismo viento y los mismos frentes da el mismo resultado en el cliente (que lo dibuja) y en el servidor
 * (que, en la fase 2, decidirá dónde llueve y caen rayos).
 *
 * <p>El cielo es una rejilla de celdas de {@link #CELL} bloques (como las nubes vanilla) que deriva con el viento.
 * <ul>
 *   <li><b>Buen tiempo:</b> pocas nubes sueltas. Su tamaño cambia por zonas (de pequeñas a enormes) y se forman y se
 *       deshacen poco a poco (el ruido tiene también una dimensión de tiempo).</li>
 *   <li><b>Frentes de lluvia y tormenta:</b> nubarrones macizos en el centro y deshilachados en el borde, que nacen,
 *       crecen y se deshacen con la vida del frente.</li>
 * </ul>
 */
public final class CloudCover {
    /** Semilla del cielo por defecto (hasta que llega el primer paquete del servidor). La de cada mundo: {@link SkySeed}. */
    public static final long SKY_SEED = 0x5707_4D20_0C10_D5L;
    /** Tamaño de una celda de nube (como en vanilla). */
    public static final int CELL = 12;
    /** Velocidad de deriva de las nubes con el viento (bloques por segundo). */
    public static final double DRIFT_SPEED = 0.9;
    /** Intensidad mínima de un frente para que haya nubarrón. */
    private static final double FRONT_MIN = 0.04;

    /** Tipo de nube de una celda. */
    public enum Kind {
        NONE, FAIR, RAIN, STORM
    }

    private CloudCover() {
    }

    /** Desplazamiento de las nubes por el viento tras {@code seconds} segundos (x, z). */
    public static double driftX(double windAngle, double seconds) {
        return Math.cos(windAngle) * DRIFT_SPEED * seconds;
    }

    public static double driftZ(double windAngle, double seconds) {
        return Math.sin(windAngle) * DRIFT_SPEED * seconds;
    }

    /**
     * Nube de buen tiempo en un punto del cielo ya desplazado por el viento ({@code u}, {@code v} = posición menos la deriva).
     *
     * @return {@code true} si hay nube
     */
    public static boolean fairCloud(long seed, double u, double v, double seconds) {
        // Zona: decide si por ahí las nubes son pequeñas o enormes y si está más o menos nublado.
        // (Se mezclan valores de ruido a escalas fijas; cambiar la escala según la posición deformaría el patrón.)
        double zone = ValueNoise.sample(seed ^ 0x51L, u / 1100.0, seconds / 2400.0, v / 1100.0);
        double big = smoothstep(0.35, 0.75, zone);
        double small = ValueNoise.fbm(seed, u / 80.0, seconds / 500.0, v / 80.0, 2);
        double large = ValueNoise.fbm(seed ^ 0x33L, u / 210.0, seconds / 900.0, v / 210.0, 4);
        double n = small * (1 - big) + large * big;
        double cloudiness = ValueNoise.sample(seed ^ 0x77L, u / 1500.0, seconds / 3000.0, v / 1500.0);
        // Pocas y sueltas: umbral alto (más aún para las pequeñas), que baja algo en las zonas más nubladas.
        double threshold = 0.72 - 0.09 * big - 0.1 * cloudiness;
        return n > threshold;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Nubarrón de los frentes en un punto: el tipo más fuerte (tormenta antes que lluvia) o {@link Kind#NONE}.
     * Usa la intensidad del frente (que ya incluye su nacimiento y su final) y un patrón pegado al cielo.
     */
    public static Kind frontCloud(long seed, double x, double z, double u, double v, double seconds, List<WeatherFront> fronts) {
        Kind best = Kind.NONE;
        for (WeatherFront front : fronts) {
            if (front.type() == FrontType.FOG) {
                continue;
            }
            double intensity = front.intensityAt(x, z);
            if (intensity < FRONT_MIN) {
                continue;
            }
            // Centro macizo; hacia el borde solo donde el patrón tiene nube (bordes deshilachados).
            double pattern = ValueNoise.fbm(seed ^ (front.id() * 0x2545F4914F6CDD1DL), u / 36.0, seconds / 400.0, v / 36.0, 2);
            boolean cloud = intensity > 0.35 || pattern > 0.75 - intensity * 1.4;
            if (!cloud) {
                continue;
            }
            Kind kind = front.type() == FrontType.THUNDERSTORM ? Kind.STORM : Kind.RAIN;
            if (kind.ordinal() > best.ordinal()) {
                best = kind;
            }
        }
        return best;
    }

    /** Nube en el centro de una celda (frentes primero; si no, buen tiempo). */
    public static Kind at(long seed, double x, double z, double windAngle, double seconds, List<WeatherFront> fronts) {
        double u = x - driftX(windAngle, seconds);
        double v = z - driftZ(windAngle, seconds);
        Kind front = frontCloud(seed, x, z, u, v, seconds, fronts);
        if (front != Kind.NONE) {
            return front;
        }
        return fairCloud(seed, u, v, seconds) ? Kind.FAIR : Kind.NONE;
    }
}
