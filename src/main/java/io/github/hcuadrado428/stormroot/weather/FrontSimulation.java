package io.github.hcuadrado428.stormroot.weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Simulación de los frentes de UNA dimensión. Java puro: no sabe nada de Minecraft.
 *
 * <p>La capa de Minecraft ({@link FrontTicker}) la llama cada pocos segundos con la posición
 * de los jugadores, para que los frentes nazcan cerca de donde hay alguien.
 *
 * <p>Se guarda con {@code FrontSavedData} (exportando/restaurando su estado con {@link #restore}).
 * Pendiente: viento dominante por bioma y choques de frentes.
 */
public final class FrontSimulation {
    private final List<WeatherFront> fronts = new ArrayList<>();
    private final Random random;
    private long nextId = 1;

    /** Ajustes provisionales; pasarán al fichero de configuración. */
    public static final int MAX_FRONTS = 8;
    public static final double SPAWN_CHANCE_PER_STEP = 0.05;
    public static final int SPAWN_DISTANCE_MIN = 256;
    public static final int SPAWN_DISTANCE_MAX = 512;

    /** Dirección del viento dominante en radianes (0 = hacia +X). Provisional: fija por mundo. */
    private double windAngle;

    public FrontSimulation(long seed) {
        this.random = new Random(seed);
        this.windAngle = random.nextDouble() * Math.PI * 2;
    }

    /**
     * Avanza la simulación.
     *
     * @param seconds  segundos de juego transcurridos desde el último paso
     * @param anchorsXZ posiciones [x, z] de los jugadores (pueden ser 0)
     * @return frentes nuevos creados en este paso (útil para logs y depuración)
     */
    public List<WeatherFront> step(double seconds, List<double[]> anchorsXZ) {
        Iterator<WeatherFront> it = fronts.iterator();
        while (it.hasNext()) {
            WeatherFront front = it.next();
            front.advance(seconds);
            if (front.isExpired()) {
                it.remove();
            }
        }

        // El viento cambia muy despacio.
        windAngle += (random.nextDouble() - 0.5) * 0.02 * seconds;

        List<WeatherFront> spawned = new ArrayList<>();
        if (!anchorsXZ.isEmpty() && fronts.size() < MAX_FRONTS && random.nextDouble() < SPAWN_CHANCE_PER_STEP) {
            double[] anchor = anchorsXZ.get(random.nextInt(anchorsXZ.size()));
            WeatherFront front = spawnUpwind(anchor[0], anchor[1], randomType());
            fronts.add(front);
            spawned.add(front);
        }
        return spawned;
    }

    /** Crea un frente "a favor de viento" del punto dado, para que el jugador lo vea llegar. */
    public WeatherFront spawnUpwind(double x, double z, FrontType type) {
        double distance = SPAWN_DISTANCE_MIN + random.nextDouble() * (SPAWN_DISTANCE_MAX - SPAWN_DISTANCE_MIN);
        double spread = (random.nextDouble() - 0.5) * 0.8;
        double fromAngle = windAngle + Math.PI + spread;
        double fx = x + Math.cos(fromAngle) * distance;
        double fz = z + Math.sin(fromAngle) * distance;
        return create(type, fx, fz);
    }

    /** Crea un frente en un punto concreto (también para comandos de depuración). */
    public WeatherFront create(FrontType type, double x, double z) {
        double radius = type.minRadius() + random.nextDouble() * (type.maxRadius() - type.minRadius());
        double speed = type.speed() * (0.75 + random.nextDouble() * 0.5);
        double vx = Math.cos(windAngle) * speed;
        double vz = Math.sin(windAngle) * speed;
        double duration = type.minDurationSeconds()
                + random.nextDouble() * (type.maxDurationSeconds() - type.minDurationSeconds());
        return new WeatherFront(nextId++, type, x, z, radius, vx, vz, 0, duration);
    }

    public void add(WeatherFront front) {
        fronts.add(front);
        nextId = Math.max(nextId, front.id() + 1);
    }

    /** El frente más intenso en un punto, o {@code null} si no hay ninguno. */
    public WeatherFront strongestAt(double x, double z) {
        WeatherFront best = null;
        double bestIntensity = 0;
        for (WeatherFront front : fronts) {
            double intensity = front.intensityAt(x, z);
            if (intensity > bestIntensity) {
                bestIntensity = intensity;
                best = front;
            }
        }
        return best;
    }

    public void clear() {
        fronts.clear();
    }

    public List<WeatherFront> fronts() {
        return Collections.unmodifiableList(fronts);
    }

    public double windAngle() {
        return windAngle;
    }

    /** Siguiente id que se asignará (para guardar el estado). */
    public long nextId() {
        return nextId;
    }

    /**
     * Restaura el estado guardado (frentes, viento y contador de ids).
     * El generador aleatorio no se guarda: tras cargar, los frentes nuevos serán distintos, y no pasa nada.
     */
    public void restore(double windAngle, long nextId, List<WeatherFront> saved) {
        this.windAngle = windAngle;
        this.fronts.clear();
        this.fronts.addAll(saved);
        long maxId = 0;
        for (WeatherFront front : saved) {
            maxId = Math.max(maxId, front.id());
        }
        this.nextId = Math.max(nextId, maxId + 1);
    }

    private FrontType randomType() {
        FrontType[] types = FrontType.values();
        return types[random.nextInt(types.length)];
    }
}
