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
    /**
     * Nubes de Stormroot en vez de las vanilla. Desactívalo con un shader pack que dibuje sus propias nubes.
     * Sin Bloomberto se lee de {@code config/stormroot-client.properties} ({@link ClientSettingsFile}).
     */
    /** El granizo puede romper cristal y paneles de cristal normales a la intemperie. */
    public static volatile BooleanSupplier hailBreaksGlass = () -> true;
    /** El viento desvía proyectiles (con Bloomberto; se combina con el ajuste del mundo {@code wind_projectiles}). */
    public static volatile BooleanSupplier windProjectiles = () -> true;
    /** El viento desvía también los disparos de los jugadores ({@code wind_player_shots}). */
    public static volatile BooleanSupplier windPlayerShots = () -> true;
    public static volatile BooleanSupplier customClouds = ClientSettingsFile::customClouds;
    /** Sombra de las nubes en el suelo. Sin Bloomberto: {@code cloud_shadows} en {@code config/stormroot-client.properties}. */
    public static volatile BooleanSupplier cloudShadows = ClientSettingsFile::cloudShadows;
    /** Indicador del clima en pantalla (solo con Bloomberto). */
    public static volatile BooleanSupplier weatherHud = () -> false;

    private StormrootConfig() {
    }

    /** Filtración activa: config (Bloomberto) Y ajuste del mundo (/stormroot settings seepage). */
    public static boolean seepageActive() {
        return seepageEnabled.getAsBoolean() && WorldSettings.seepage;
    }

    /** La filtración puede cambiar bloques: config Y ajuste del mundo (cave_changes). */
    /** El granizo rompe cristal: lo permiten la config (Bloomberto) y el ajuste del mundo. */
    public static boolean hailBreaksGlassActive() {
        return hailBreaksGlass.getAsBoolean() && WorldSettings.hailBreaksGlass;
    }

    /** El viento empuja proyectiles: config Y ajuste del mundo. */
    public static boolean windProjectilesActive() {
        return windProjectiles.getAsBoolean() && WorldSettings.windProjectiles;
    }

    /** ...también los de los jugadores. */
    public static boolean windPlayerShotsActive() {
        return windPlayerShots.getAsBoolean() && WorldSettings.windPlayerShots;
    }

    public static boolean caveChangesActive() {
        return seepageWorldChanges.getAsBoolean() && WorldSettings.caveChanges;
    }

    /** Probabilidad de raíz de rayo; 0 si el mundo las tiene apagadas (lightning_roots). */
    public static double effectiveRootChance() {
        return WorldSettings.lightningRoots ? lightningRootChance.getAsDouble() : 0.0;
    }
}
