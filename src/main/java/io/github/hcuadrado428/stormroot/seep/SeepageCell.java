package io.github.hcuadrado428.stormroot.seep;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro climático de un chunk. Java puro.
 *
 * <ul>
 *   <li>{@code surface}: humedad del suelo, de -1 (sequía) a 1 (empapado). Sube rápido con la lluvia y baja despacio.</li>
 *   <li>{@code deep}: humedad del subsuelo. Sigue a {@code surface} con retraso: es lo que ven las cuevas.</li>
 *   <li>{@code frost}: helada de las cuevas, de 0 a 1. Sube con la ventisca encima; baja muy despacio sin ella, o de
 *       golpe en un deshielo masivo ({@link #massiveThaw}).</li>
 *   <li>{@code changes}: bloques que Stormroot ha cambiado en este chunk, con lo que había antes, para deshacerlos.</li>
 * </ul>
 * El agua estancada (charcos y pozas) retiene la humedad: mientras haya, el chunk se seca más despacio.
 */
public final class SeepageCell {
    /** Con agua estancada, el suelo se seca a esta fracción de la velocidad normal. */
    public static final double STANDING_WATER_RETENTION = 0.4;

    private double surface;
    private double deep;
    private double frost;
    private final List<Change> changes;

    public SeepageCell(double surface, double deep, List<Change> changes) {
        this(surface, deep, 0, changes);
    }

    public SeepageCell(double surface, double deep, double frost, List<Change> changes) {
        this.surface = clamp(surface);
        this.deep = clamp(deep);
        this.frost = Math.max(0, Math.min(1, frost));
        this.changes = new ArrayList<>(changes);
    }

    public SeepageCell() {
        this(0, 0, 0, List.of());
    }

    /** Ventisca a tope: la cueva se hiela del todo en unos 8 minutos. */
    public static final double FREEZE_PER_SECOND = 1.0 / 480;
    /** Sin ventisca: se deshiela MUY despacio (de helada del todo a nada en unas 3 horas de juego). */
    public static final double SLOW_THAW_PER_SECOND = 1.0 / 10800;
    /** Deshielo masivo: de helada del todo a nada en un minuto. */
    public static final double FAST_THAW_PER_SECOND = 1.0 / 60;
    /** Humedad que deja un deshielo masivo (la nieve derretida baja de golpe al subsuelo). */
    public static final double THAW_FLOOD = 0.9;

    /**
     * Helada: {@code blizzard} = intensidad de la ventisca encima (0–1); {@code thaw} = {@code true} si hay un deshielo
     * masivo encima (una tormenta o, más adelante, una ola de calor sobre una cueva helada).
     */
    public void updateFrost(double blizzard, boolean thaw, double seconds) {
        thawing = thaw && frost > 0;
        if (thawing) {
            double melted = Math.min(frost, FAST_THAW_PER_SECOND * seconds);
            frost -= melted;
            // La nieve derretida: humedad de golpe (inundación).
            surface = clamp(surface + melted * THAW_FLOOD * 2);
            deep = clamp(deep + melted * THAW_FLOOD);
        } else if (blizzard > 0.05) {
            frost = Math.min(1, frost + blizzard * FREEZE_PER_SECOND * seconds);
        } else {
            frost = Math.max(0, frost - SLOW_THAW_PER_SECOND * seconds);
        }
    }

    public double frost() {
        return frost;
    }

    /** Intensidad del monzón encima en la última actualización (no se guarda). */
    private transient double monsoon;

    public double monsoon() {
        return monsoon;
    }

    public void setMonsoon(double value) {
        monsoon = value;
    }

    /** Deshielo masivo en curso (no se guarda). */
    private transient boolean thawing;

    public boolean thawing() {
        return thawing;
    }

    /** Fija la helada (comando de depuración y self-test). */
    public void setFrost(double value) {
        frost = Math.max(0, Math.min(1, value));
    }

    /** Avanza {@code seconds} segundos con una lluvia de intensidad {@code rain} (0–1) encima. */
    public void update(double rain, double seconds, SeepageRates rates) {
        update(rain, 0, seconds, rates);
    }

    /**
     * Como {@link #update(double, double, SeepageRates)}, con niebla de intensidad {@code fog} (0–1): humedece a la
     * mitad que la lluvia y como mucho hasta {@link #FOG_CAP} (barro y musgo sí; charcos y pozas no).
     */
    public void update(double rain, double fog, double seconds, SeepageRates rates) {
        double retention = hasStandingWater() ? STANDING_WATER_RETENTION : 1.0;
        foggy = fog > 0.05;
        if (rain > 0.05) {
            surface += rain * rates.wetPerSecond() * seconds;
        } else if (foggy && surface < FOG_CAP) {
            surface = Math.min(FOG_CAP, surface + fog * 0.5 * rates.wetPerSecond() * seconds);
        } else if (foggy) {
            // Con niebla no sube más, pero tampoco se seca.
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

    /**
     * Ola de calor de intensidad {@code heat} (0–1) encima, sin lluvia: el suelo se seca tres veces más rápido y la
     * sequía avanza cuatro veces más rápido (grietas antes). Se llama después de {@link #update}.
     */
    public void applyHeat(double heat, double seconds, SeepageRates rates) {
        if (heat <= 0.05) {
            return;
        }
        double retention = hasStandingWater() ? STANDING_WATER_RETENTION : 1.0;
        if (surface > 0) {
            surface -= heat * 2 * rates.dryPerSecond() * retention * seconds;
        } else {
            surface -= heat * 3 * rates.droughtPerSecond() * retention * seconds;
        }
        surface = clamp(surface);
        double follow = Math.min(1.0, seconds / rates.deepLagSeconds());
        deep = clamp(deep + (surface - deep) * follow * heat);
    }

    /** Humedad máxima que da la niebla sola (por debajo de los charcos, {@code SeepageTicker.PUDDLE_AT} = 0,55). */
    public static final double FOG_CAP = 0.5;

    /** Hubo niebla encima en la última actualización (no se guarda). */
    private transient boolean foggy;

    /** Para el self-test: niebla encima sin pasar por {@link #update}. */
    public void setFoggyForTest(boolean value) {
        foggy = value;
    }

    public boolean foggy() {
        return foggy;
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
        return Math.abs(surface) < 0.02 && Math.abs(deep) < 0.02 && frost < 0.01 && changes.isEmpty();
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
        CRACKED("cracked", false),
        /** Capa de nieve en el suelo de una cueva helada (antes aire). */
        SNOW_FLOOR("snow_floor", false),
        /** Suelo de roca cubierto de hielo compacto. */
        ICE_FLOOR("ice_floor", false),
        /** Agua natural quieta congelada (se restaura el agua). */
        FROZEN("frozen", false),
        /** Carámbano colgando del techo (antes aire). */
        ICICLE("icicle", false),
        /** Cristal de hielo en el suelo (antes aire). */
        ICE_CRYSTAL("ice_crystal", false),
        /** Orilla inundada por el monzón: el suelo pasa a ser agua y vuelve a su material al bajar. */
        FLOOD("flood", false),
        /** Cascada temporal: agua que cae del techo de una cueva durante el monzón (antes aire). */
        CASCADE("cascade", false);

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
