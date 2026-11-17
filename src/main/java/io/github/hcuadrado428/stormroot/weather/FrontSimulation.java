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
 * El tipo de cada frente nuevo depende del bioma donde nace ({@link ClimateZone}). Pendiente: choques de frentes.
 */
public final class FrontSimulation {
    private final List<WeatherFront> fronts = new ArrayList<>();
    private final Random random;
    private long nextId = 1;

    /** Valores por defecto (la config puede cambiarlos con {@link #setMaxFronts} y {@link #setSpawnChance}). */
    public static final int MAX_FRONTS = 8;
    public static final double SPAWN_CHANCE_PER_STEP = 0.05;
    public static final int SPAWN_DISTANCE_MIN = 256;
    public static final int SPAWN_DISTANCE_MAX = 512;

    private int maxFronts = MAX_FRONTS;
    private double spawnChance = SPAWN_CHANCE_PER_STEP;

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
        return step(seconds, anchorsXZ, (x, z) -> ClimateZone.TEMPERATE);
    }

    /**
     * Como {@link #step(double, List)}, pero el tipo de frente nuevo depende del bioma donde nace
     * ({@link ClimateZone#weight}). Si en ese sitio no puede nacer ninguno (p. ej. niebla en el desierto), este paso no
     * nace nada.
     */
    public List<WeatherFront> step(double seconds, List<double[]> anchorsXZ, ClimateZone.Lookup climate) {
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
        if (!anchorsXZ.isEmpty() && fronts.size() < maxFronts && random.nextDouble() < spawnChance) {
            double[] anchor = anchorsXZ.get(random.nextInt(anchorsXZ.size()));
            double[] at = upwindPoint(anchor[0], anchor[1]);
            FrontType type = typeFor(climate.at(at[0], at[1]));
            if (type != null) {
                WeatherFront front = create(type, at[0], at[1]);
                fronts.add(front);
                spawned.add(front);
            }
        }
        return spawned;
    }

    /** Crea un frente "a favor de viento" del punto dado, para que el jugador lo vea llegar. */
    public WeatherFront spawnUpwind(double x, double z, FrontType type) {
        double[] at = upwindPoint(x, z);
        return create(type, at[0], at[1]);
    }

    private double[] upwindPoint(double x, double z) {
        double distance = SPAWN_DISTANCE_MIN + random.nextDouble() * (SPAWN_DISTANCE_MAX - SPAWN_DISTANCE_MIN);
        double spread = (random.nextDouble() - 0.5) * 0.8;
        double fromAngle = windAngle + Math.PI + spread;
        return new double[] {x + Math.cos(fromAngle) * distance, z + Math.sin(fromAngle) * distance};
    }

    /** Tipo al azar según los pesos de la zona, o {@code null} si en esa zona no nace ninguno. */
    public FrontType typeFor(ClimateZone zone) {
        double total = 0;
        for (FrontType type : FrontType.values()) {
            total += zone.weight(type);
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (FrontType type : FrontType.values()) {
            pick -= zone.weight(type);
            if (pick < 0) {
                return type;
            }
        }
        return FrontType.values()[FrontType.values().length - 1];
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

    public void setMaxFronts(int maxFronts) {
        this.maxFronts = Math.max(0, maxFronts);
    }

    public void setSpawnChance(double spawnChance) {
        this.spawnChance = Math.max(0.0, Math.min(1.0, spawnChance));
    }

    /**
     * Pronóstico para un punto: el primer frente que lo cubrirá en los próximos {@code horizonSeconds} segundos.
     * Simula copias de los frentes, así que no cambia nada.
     *
     * @param threshold intensidad mínima para considerar que el frente "ha llegado"
     * @return el pronóstico, o {@code null} si no llega ninguno
     */
    public Forecast forecast(double x, double z, double horizonSeconds, double stepSeconds, double threshold) {
        Forecast best = null;
        for (WeatherFront original : fronts) {
            WeatherFront front = original.copy();
            double peak = 0;
            double eta = -1;
            for (double t = 0; t <= horizonSeconds && !front.isExpired(); t += stepSeconds) {
                double intensity = front.intensityAt(x, z);
                if (intensity >= threshold && eta < 0) {
                    eta = t;
                }
                peak = Math.max(peak, intensity);
                front.advance(stepSeconds);
            }
            if (eta >= 0 && (best == null || eta < best.etaSeconds()
                    || (eta == best.etaSeconds() && peak > best.peakIntensity()))) {
                best = new Forecast(original.type(), eta, peak);
            }
        }
        return best;
    }

    /** Resultado de {@link #forecast}: tipo de frente, segundos hasta que llegue (0 = ya está aquí) e intensidad máxima. */
    public record Forecast(FrontType type, double etaSeconds, double peakIntensity) {
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

}
