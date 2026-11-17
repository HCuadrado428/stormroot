package io.github.hcuadrado428.stormroot.seep;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro climático de un chunk. Java puro.
 *
 * <ul>
 *   <li>{@code surface}: humedad del suelo, de -1 (sequía) a 1 (empapado). Sube rápido con la lluvia y baja despacio.</li>
 *   <li>{@code deep}: humedad del subsuelo. Sigue a {@code surface} con retraso: es lo que ven las cuevas.</li>
 *   <li>{@code changes}: bloques que Stormroot ha cambiado en este chunk (charcos, barro), para poder deshacerlos.</li>
 * </ul>
 */
public final class SeepageCell {
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

    /**
     * Avanza {@code seconds} segundos con una lluvia de intensidad {@code rain} (0–1) encima.
     */
    public void update(double rain, double seconds, SeepageRates rates) {
        if (rain > 0.05) {
            surface += rain * rates.wetPerSecond() * seconds;
        } else if (surface > 0) {
            surface -= rates.dryPerSecond() * seconds;
        } else {
            // Ya seco: la sequía avanza más despacio todavía.
            surface -= rates.droughtPerSecond() * seconds;
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

    /** {@code true} si no aporta nada y se puede olvidar. */
    public boolean isNeutral() {
        return Math.abs(surface) < 0.02 && Math.abs(deep) < 0.02 && changes.isEmpty();
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /** Un bloque cambiado por Stormroot: posición empaquetada (BlockPos#asLong) y tipo de cambio. */
    public record Change(long pos, Kind kind) {
    }

    public enum Kind {
        /** Fuente de agua en el suelo de una cueva. Se quita si sigue siendo una fuente de agua. */
        PUDDLE("puddle"),
        /** Tierra convertida en barro. Vuelve a tierra si sigue siendo barro. */
        MUD("mud");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return PUDDLE;
        }
    }
}
