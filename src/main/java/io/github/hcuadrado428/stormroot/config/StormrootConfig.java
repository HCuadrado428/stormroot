package io.github.hcuadrado428.stormroot.config;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Ajustes de Stormroot. Java puro: el resto del mod lee de aquí.
 *
 * <p>Sin Bloomberto son los valores por defecto. Con Bloomberto instalado, {@code compat.bloomberto.BloombertoCompat}
 * cambia cada proveedor por el valor de {@code config/stormroot.toml} (editable también desde la pantalla de config).
 */
public final class StormrootConfig {
    // Servidor
    public static volatile IntSupplier maxFronts = () -> 8;
    public static volatile DoubleSupplier frontSpawnChance = () -> 0.05;
    public static volatile DoubleSupplier lightningCrystalChance = () -> 0.25;
    public static volatile DoubleSupplier lightningRootChance = () -> 0.35;
    /** Registro climático por chunk (humedad del suelo y del subsuelo). */
    public static volatile BooleanSupplier seepageEnabled = () -> true;
    /** Si la filtración puede cambiar bloques en las cuevas (charcos, barro). */
    public static volatile BooleanSupplier seepageWorldChanges = () -> true;
    /** Multiplicador de velocidad de la filtración (1 = normal, 10 = para pruebas). */
    public static volatile DoubleSupplier seepageSpeed = () -> 1.0;
    /** Máximo de bloques que la filtración puede cambiar en un chunk (charcos, pozas, barro, musgo, grietas). */
    public static volatile IntSupplier seepageMaxChangesPerChunk = () -> 48;

    // Cliente
    public static volatile IntSupplier edgeParticlesPerTick = () -> 6;
    /** Efectos de pantalla de tormenta y niebla (solo con Bloomberto). */
    public static volatile BooleanSupplier stormScreenEffects = () -> true;
    /** Temblor de cámara con los rayos cercanos (solo con Bloomberto). */
    public static volatile BooleanSupplier lightningCameraShake = () -> true;
    /** Nubes de los frentes al estilo vanilla, visibles de lejos (solo con Bloomberto). */
    public static volatile BooleanSupplier distantFronts = () -> true;
    /** Indicador del clima en pantalla (solo con Bloomberto). */
    public static volatile BooleanSupplier weatherHud = () -> false;

    private StormrootConfig() {
    }

    /** Filtración activa: config (Bloomberto) Y ajuste del mundo (/stormroot settings seepage). */
    public static boolean seepageActive() {
        return seepageEnabled.getAsBoolean() && WorldSettings.seepage;
    }

    /** La filtración puede cambiar bloques: config Y ajuste del mundo (cave_changes). */
    public static boolean caveChangesActive() {
        return seepageWorldChanges.getAsBoolean() && WorldSettings.caveChanges;
    }

    /** Probabilidad de raíz de rayo; 0 si el mundo las tiene apagadas (lightning_roots). */
    public static double effectiveRootChance() {
        return WorldSettings.lightningRoots ? lightningRootChance.getAsDouble() : 0.0;
    }
}
