package io.github.hcuadrado428.stormroot.seep;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registro climático de todos los chunks de una dimensión. Java puro.
 * Solo guarda chunks con algo que contar: los neutros se olvidan con {@link #prune()}.
 */
public final class SeepageField {
    private final Map<Long, SeepageCell> cells = new HashMap<>();

    /** Misma clave que {@code ChunkPos.asLong(x, z)}. */
    public static long key(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    public static int keyX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    public static int keyZ(long key) {
        return (int) (key >>> 32 & 0xFFFFFFFFL);
    }

    public SeepageCell getOrCreate(int chunkX, int chunkZ) {
        return cells.computeIfAbsent(key(chunkX, chunkZ), k -> new SeepageCell());
    }

    /** La celda si existe, o {@code null} (un chunk sin registro es neutro: 0). */
    public SeepageCell get(int chunkX, int chunkZ) {
        return cells.get(key(chunkX, chunkZ));
    }

    public double deep(int chunkX, int chunkZ) {
        SeepageCell cell = get(chunkX, chunkZ);
        return cell == null ? 0 : cell.deep();
    }

    public void put(long key, SeepageCell cell) {
        cells.put(key, cell);
    }

    public void prune() {
        cells.values().removeIf(SeepageCell::isNeutral);
    }

    public Map<Long, SeepageCell> cells() {
        return Collections.unmodifiableMap(cells);
    }

    public void clear() {
        cells.clear();
    }
}
