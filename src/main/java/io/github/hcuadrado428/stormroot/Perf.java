package io.github.hcuadrado428.stormroot;

import java.util.Map;
import java.util.TreeMap;

/**
 * Cronómetros baratos para medir lo que cuesta cada parte de Stormroot (Java puro). Solo miden si el juego arranca con
 * {@code -Dstormroot.perf=true} o en el self-test; si no, {@link #start()} devuelve 0 y {@link #end} no hace nada.
 *
 * <pre>
 * long t = Perf.start();
 * ...
 * Perf.end("sky.shadows", t);
 * </pre>
 */
public final class Perf {
    public static final boolean ON = Boolean.getBoolean("stormroot.perf") || System.getProperty("stormroot.selfTest") != null;

    private static final Map<String, Stat> STATS = new TreeMap<>();

    private Perf() {
    }

    public static long start() {
        return ON ? System.nanoTime() : 0;
    }

    public static void end(String name, long start) {
        if (!ON || start == 0) {
            return;
        }
        long ns = System.nanoTime() - start;
        synchronized (STATS) {
            STATS.computeIfAbsent(name, n -> new Stat()).add(ns);
        }
    }

    /** Media, máximo y número de medidas de {@code name} (null si no hay). */
    public static Stat get(String name) {
        synchronized (STATS) {
            Stat s = STATS.get(name);
            return s == null ? null : s.copy();
        }
    }

    /** Empieza de cero (p. ej. para medir solo una parte de la prueba). */
    public static void reset(String prefix) {
        synchronized (STATS) {
            STATS.keySet().removeIf(n -> n.startsWith(prefix));
        }
    }

    /** Una línea por medida que empieza por {@code prefix}: {@code nombre media=… µs p99≈… máx=… n=…}. */
    public static String report(String prefix) {
        StringBuilder sb = new StringBuilder();
        synchronized (STATS) {
            for (Map.Entry<String, Stat> e : STATS.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    sb.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    public static final class Stat {
        private long count;
        private long total;
        private long max;
        /** Histograma en escalones de potencias de 2 (en ns) para estimar el percentil 99. */
        private final long[] buckets = new long[40];

        void add(long ns) {
            count++;
            total += ns;
            max = Math.max(max, ns);
            buckets[Math.min(buckets.length - 1, 63 - Long.numberOfLeadingZeros(Math.max(1, ns)))]++;
        }

        Stat copy() {
            Stat s = new Stat();
            s.count = count;
            s.total = total;
            s.max = max;
            System.arraycopy(buckets, 0, s.buckets, 0, buckets.length);
            return s;
        }

        public long count() {
            return count;
        }

        /** Media en microsegundos. */
        public double meanMicros() {
            return count == 0 ? 0 : total / 1000.0 / count;
        }

        public double maxMicros() {
            return max / 1000.0;
        }

        /** Percentil 99 aproximado (límite superior de su escalón), en microsegundos. */
        public double p99Micros() {
            long limit = (long) Math.ceil(count * 0.99);
            long seen = 0;
            for (int i = 0; i < buckets.length; i++) {
                seen += buckets[i];
                if (seen >= limit) {
                    return Math.min(max, (1L << (i + 1))) / 1000.0;
                }
            }
            return maxMicros();
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "media=%.1fµs p99≈%.0fµs máx=%.0fµs n=%d", meanMicros(), p99Micros(), maxMicros(), count);
        }
    }
}
