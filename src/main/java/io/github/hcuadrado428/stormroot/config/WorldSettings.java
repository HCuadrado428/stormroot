package io.github.hcuadrado428.stormroot.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interruptores que se guardan EN EL MUNDO (con los frentes, en {@code FrontSavedData}) y se cambian con
 * {@code /stormroot settings}. Así un servidor puede apagar cosas aunque no tenga Bloomberto. Java puro.
 *
 * <p>Se combinan con la config de Bloomberto: una función está activa solo si las dos lo permiten
 * (ver los métodos de {@link StormrootConfig}).
 */
public final class WorldSettings {
    /** La filtración (humedad del suelo y del subsuelo) está activa. */
    public static volatile boolean seepage = true;
    /** La filtración puede cambiar bloques de las cuevas (charcos, barro, musgo, grietas). */
    public static volatile boolean caveChanges = true;
    /** Los rayos pueden hacer crecer raíces de rayo. */
    public static volatile boolean lightningRoots = true;

    private WorldSettings() {
    }

    /** Nombre del ajuste → valor, en el orden en que se muestran. */
    public static Map<String, Boolean> all() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("seepage", seepage);
        map.put("cave_changes", caveChanges);
        map.put("lightning_roots", lightningRoots);
        return map;
    }

    /** @return {@code false} si el nombre no existe */
    public static boolean set(String name, boolean value) {
        switch (name) {
            case "seepage" -> seepage = value;
            case "cave_changes" -> caveChanges = value;
            case "lightning_roots" -> lightningRoots = value;
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void reset() {
        seepage = true;
        caveChanges = true;
        lightningRoots = true;
    }
}
