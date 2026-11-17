package io.github.hcuadrado428.stormroot.seep;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro climático de un chunk. Java puro.
 *
 * <ul>
 *   <li>{@code surface}: humedad del suelo, de -1 (sequía) a 1 (empapado). Sube rápido con la lluvia y baja despacio.</li>
 *   <li>{@code deep}: humedad del subsuelo. Sigue a {@code surface} con retraso: es lo que ven las cuevas.</li>
 *   <li>{@code changes}: bloques que Stormroot ha cambiado en este chunk, con lo que había antes, para deshacerlos.</li>
 * </ul>
 * El agua estancada (charcos y pozas) retiene la humedad: mientras haya, el chunk se seca más despacio.
 */
public final class SeepageCell {
    /** Con agua estancada, el suelo se seca a esta fracción de la velocidad normal. */
    public static final double STANDING_WATER_RETENTION = 0.4;

    private double surface;
    private double deep;
    private final List<Change> changes;

    public SeepageCell(double surface, double deep, List<Change> changes) {
        this.surface = clamp(surface);
        this.deep = clamp(deep);
        this.changes = new ArrayList<>(changes);
    }

    public SeepageCell() {
        this(0, 0, List.of());
    }

    /** Avanza {@code seconds} segundos con una lluvia de intensidad {@code rain} (0–1) encima. */
    public void update(double rain, double seconds, SeepageRates rates) {
        double retention = hasStandingWater() ? STANDING_WATER_RETENTION : 1.0;
        if (rain > 0.05) {
            surface += rain * rates.wetPerSecond() * seconds;
        } else if (surface > 0) {
            surface -= rates.dryPerSecond() * retention * seconds;
        } else {
            // Ya seco: la sequía avanza más despacio todavía.
            surface -= rates.droughtPerSecond() * retention * seconds;
        }
        surface = clamp(surface);
        double follow = Math.min(1.0, seconds / rates.deepLagSeconds());
        deep = clamp(deep + (surface - deep) * follow);
    }

    public double surface() {
        return surface;
    }

    public double deep() {
        return deep;
    }

    /** Fija ambos valores (comando de depuración). */
    public void set(double value) {
        surface = clamp(value);
        deep = clamp(value);
    }

    public List<Change> changes() {
        return changes;
    }

    public boolean hasStandingWater() {
        for (Change change : changes) {
            if (change.kind().isWater()) {
                return true;
            }
        }
        return false;
    }

    public int count(Kind kind) {
        int n = 0;
        for (Change change : changes) {
            if (change.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    /**
     * Índice del último cambio de alguno de esos tipos (en ese orden de prioridad), o -1.
     * Se deshace siempre lo más reciente primero, para que el agua "se retire" por donde llegó.
     */
    public int lastIndexOf(Kind... kinds) {
        for (Kind kind : kinds) {
            for (int i = changes.size() - 1; i >= 0; i--) {
                if (changes.get(i).kind() == kind) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** {@code true} si no aporta nada y se puede olvidar. */
    public boolean isNeutral() {
        return Math.abs(surface) < 0.02 && Math.abs(deep) < 0.02 && changes.isEmpty();
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /**
     * Un bloque cambiado por Stormroot: posición empaquetada (BlockPos#asLong), tipo de cambio y material original
     * (id de {@code CaveMaterials}; vacío si antes era aire).
     */
    public record Change(long pos, Kind kind, String original) {
    }

    public enum Kind {
        /** Bloque del suelo sustituido por agua (charco de 1 bloque de hondo, encerrado). Se restaura el original. */
        PUDDLE("puddle", true),
        /** Aire de un hoyo natural rellenado con agua (poza). Se vacía. */
        POOL("pool", true),
        /** Tierra convertida en barro. */
        MUD("mud", false),
        /** Piedra cubierta de musgo tras mucho tiempo húmeda. Solo se deshace con sequía. */
        MOSS("moss", false),
        /** Tierra o barro agrietados por la sequía (tierra gruesa). Se deshace al volver la humedad. */
        CRACKED("cracked", false);

        private final String id;
        private final boolean water;

        Kind(String id, boolean water) {
            this.id = id;
            this.water = water;
        }

        public String id() {
            return id;
        }

        public boolean isWater() {
            return water;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return POOL;
        }
    }
}
