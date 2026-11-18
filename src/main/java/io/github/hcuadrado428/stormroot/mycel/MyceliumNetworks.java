package io.github.hcuadrado428.stormroot.mycel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué chunks ocupa cada red de micelio (Java puro). Una red nace en un chunk y va reclamando los chunks a los que
 * crece, hasta {@link #MAX_CHUNKS}. Los chunks son claves {@code SeepageField.key(x, z)} (aquí solo son números).
 * Lo de "no salir de su bioma" lo decide quien llama (necesita el mundo).
 */
public final class MyceliumNetworks {
    /** Tamaño máximo de una red: como un megabioma pequeño. */
    public static final int MAX_CHUNKS = 30;

    /** Estado de una red: salvaje (por defecto), calmada (venciste a su guardián) o domesticada (corazón de micelio). */
    public enum State {
        WILD, CALM, TAMED;

        public static State byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : WILD;
        }
    }

    /** Una red grande tiene guardián a partir de estos chunks. */
    public static final int GUARDIAN_AT = 8;

    private final Map<Integer, Set<Long>> chunksByNetwork = new HashMap<>();
    private final Map<Integer, State> stateByNetwork = new HashMap<>();
    private final Map<Long, Integer> networkByChunk = new HashMap<>();
    private int nextId = 1;

    /** Red que ocupa ese chunk, o 0 si ninguna. */
    public int networkAt(long chunk) {
        return networkByChunk.getOrDefault(chunk, 0);
    }

    /** Red nueva que ocupa {@code chunk} (si ya había una ahí, devuelve esa). */
    public int create(long chunk) {
        int existing = networkAt(chunk);
        if (existing != 0) {
            return existing;
        }
        int id = nextId++;
        Set<Long> chunks = new LinkedHashSet<>();
        chunks.add(chunk);
        chunksByNetwork.put(id, chunks);
        networkByChunk.put(chunk, id);
        return id;
    }

    /**
     * ¿Puede la red del chunk {@code from} crecer hasta {@code to}? Si {@code to} está libre y la red no ha llegado a su
     * tamaño máximo, lo reclama. Si {@code from} no es de nadie (red antigua o perdida), se le crea una red.
     */
    public boolean tryGrow(long from, long to) {
        int id = networkAt(from);
        if (id == 0) {
            id = create(from);
        }
        int owner = networkAt(to);
        if (owner == id) {
            return true;
        }
        if (owner != 0) {
            // Chunk de otra red: las redes se tocan, pero no se roban chunks.
            return true;
        }
        Set<Long> chunks = chunksByNetwork.get(id);
        if (chunks.size() >= MAX_CHUNKS || state(id) == State.CALM) {
            return false;
        }
        chunks.add(to);
        networkByChunk.put(to, id);
        return true;
    }

    public State state(int id) {
        return stateByNetwork.getOrDefault(id, State.WILD);
    }

    /** Estado de la red que ocupa ese chunk (salvaje si no hay red). */
    public State stateAt(long chunk) {
        return state(networkAt(chunk));
    }

    public void setState(int id, State state) {
        if (id != 0 && chunksByNetwork.containsKey(id)) {
            stateByNetwork.put(id, state);
        }
    }

    /** ¿Crece la red? Las calmadas no; las salvajes y domesticadas, sí (las domesticadas solo por roca y tierra). */
    public boolean grows(long chunk) {
        return stateAt(chunk) != State.CALM;
    }

    public int size(int id) {
        Set<Long> chunks = chunksByNetwork.get(id);
        return chunks == null ? 0 : chunks.size();
    }

    public int count() {
        return chunksByNetwork.size();
    }

    public void clear() {
        chunksByNetwork.clear();
        stateByNetwork.clear();
        networkByChunk.clear();
        nextId = 1;
    }

    /** Para guardar: una entrada por red. */
    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<Integer, Set<Long>> e : chunksByNetwork.entrySet()) {
            list.add(new Entry(e.getKey(), new ArrayList<>(e.getValue()), state(e.getKey()).ordinal()));
        }
        return list;
    }

    public void load(List<Entry> entries) {
        clear();
        for (Entry e : entries) {
            Set<Long> chunks = new LinkedHashSet<>(e.chunks());
            chunksByNetwork.put(e.id(), chunks);
            for (long c : chunks) {
                networkByChunk.putIfAbsent(c, e.id());
            }
            nextId = Math.max(nextId, e.id() + 1);
            if (e.state() != 0) {
                stateByNetwork.put(e.id(), State.byId(e.state()));
            }
        }
    }

    public record Entry(int id, List<Long> chunks, int state) {
        public Entry(int id, List<Long> chunks) {
            this(id, chunks, 0);
        }
    }
}
