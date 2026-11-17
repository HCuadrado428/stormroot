package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Copia en el cliente de los frentes cercanos que manda el servidor. Java puro (sin clases de cliente),
 * así que los handlers de red pueden llamarla sin miedo a cargar clases de cliente en un servidor.
 *
 * <p>Entre paquete y paquete (cada 5 s) se extrapola: cada tick se mueven los frentes con su velocidad.
 */
public final class ClientFronts {
    private static final List<WeatherFront> FRONTS = new ArrayList<>();
    private static boolean active;
    private static double windAngle;
    private static float seep;
    private static long skySeed = io.github.hcuadrado428.stormroot.sky.CloudCover.SKY_SEED;

    private ClientFronts() {
    }

    /** Llamado al recibir un paquete del servidor (en el hilo principal del cliente). */
    public static synchronized void accept(boolean serverControlsWeather, double wind, float seepage, long sky,
                                           List<WeatherFront> fronts) {
        active = serverControlsWeather;
        skySeed = sky;
        windAngle = wind;
        seep = seepage;
        FRONTS.clear();
        for (WeatherFront front : fronts) {
            FRONTS.add(front.copy());
        }
    }

    public static synchronized void advance(double seconds) {
        for (WeatherFront front : FRONTS) {
            front.advance(seconds);
        }
        FRONTS.removeIf(WeatherFront::isExpired);
    }

    /** Intensidad máxima (0–1) de los frentes de ese tipo en un punto. */
    public static synchronized double intensity(FrontType type, double x, double z) {
        double best = 0;
        for (WeatherFront front : FRONTS) {
            if (front.type() == type) {
                best = Math.max(best, front.intensityAt(x, z));
            }
        }
        return best;
    }

    /**
     * La lista viva, sin copiar. Solo desde el hilo principal del cliente (el mismo que la cambia): la usa la lluvia
     * de cada columna, que se pregunta cientos de veces por fotograma.
     */
    public static List<WeatherFront> live() {
        return Collections.unmodifiableList(FRONTS);
    }

    public static synchronized List<WeatherFront> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(FRONTS));
    }

    /** {@code true} si el servidor tiene Stormroot y controla el clima. */
    public static synchronized boolean active() {
        return active;
    }

    public static synchronized double windAngle() {
        return windAngle;
    }

    /** Semilla del cielo del mundo actual (la manda el servidor). */
    public static synchronized long skySeed() {
        return skySeed;
    }

    /** Humedad del subsuelo donde está el jugador (-1 sequía … 1 empapado). */
    public static synchronized float seep() {
        return seep;
    }

    /** Al salir del mundo. */
    public static synchronized void reset() {
        active = false;
        seep = 0;
        FRONTS.clear();
    }
}
