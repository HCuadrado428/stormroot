package io.github.hcuadrado428.stormroot.sky;

import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.List;

/**
 * Dónde cae la lluvia: solo bajo los nubarrones de lluvia y tormenta de {@link CloudCover}. Java puro.
 *
 * <p>Lo consulta {@code mixin/BiomePrecipitationMixin} cada vez que el juego pregunta si llueve en un bloque: el
 * servidor (apagar fuegos, calderos, nieve, rayos vanilla) y el cliente (gotas y partículas). Cada lado registra su
 * fuente: el servidor la suya ({@code FrontTicker}) y el cliente la suya ({@code ClientFronts}). En un solo jugador
 * los dos viven en el mismo proceso; se distinguen por el hilo del servidor.
 */
public final class SkyWeather {
    /** Lo que hace falta saber del cielo en un lado. */
    public interface Source {
        /** {@code false} = Stormroot no controla el clima aquí: no se toca nada (vanilla). */
        boolean active();

        long seed();

        double windAngle();

        /** Tiempo de juego en segundos (el mismo en servidor y cliente: {@code getGameTime() / 20}). */
        double seconds();

        List<WeatherFront> fronts();
    }

    private static volatile Thread serverThread;
    private static volatile Source server;
    private static volatile Thread clientThread;
    private static volatile Source client;

    private SkyWeather() {
    }

    public static void setServer(Thread thread, Source source) {
        serverThread = thread;
        server = source;
    }

    /** La fuente del servidor (o {@code null} si no hay servidor). Solo desde el hilo del servidor. */
    public static Source serverSource() {
        return server;
    }

    public static void clearServer() {
        serverThread = null;
        server = null;
    }

    /** Desde el hilo principal del cliente (el que dibuja y hace los ticks del cliente). */
    public static void setClient(Thread thread, Source source) {
        clientThread = thread;
        client = source;
    }

    /**
     * Solo los hilos principales: los de generación de mundo (que también miran la precipitación, p. ej. la nieve
     * de los chunks nuevos) no se tocan.
     */
    private static Source current() {
        Thread thread = Thread.currentThread();
        if (thread == serverThread) {
            return server;
        }
        return thread == clientThread ? client : null;
    }

    /**
     * {@code true} si en (x, z) NO puede llover porque no hay nubarrón encima (y Stormroot controla el clima).
     * {@code false} = se deja lo que diga vanilla.
     */
    public static boolean noRainCloudAt(double x, double z) {
        Source source = current();
        if (source == null || !source.active()) {
            return false;
        }
        return rainCloudAt(source, x, z) == CloudCover.Kind.NONE;
    }

    /** Nubarrón (lluvia o tormenta) sobre (x, z) según una fuente, o {@link CloudCover.Kind#NONE}. */
    public static CloudCover.Kind rainCloudAt(Source source, double x, double z) {
        double seconds = source.seconds();
        double wind = source.windAngle();
        double u = x - CloudCover.driftX(wind, seconds);
        double v = z - CloudCover.driftZ(wind, seconds);
        return CloudCover.frontCloud(source.seed(), x, z, u, v, seconds, source.fronts());
    }
}
