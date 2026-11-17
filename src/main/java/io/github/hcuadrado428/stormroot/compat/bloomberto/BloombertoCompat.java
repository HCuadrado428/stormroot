package io.github.hcuadrado428.stormroot.compat.bloomberto;

import com.dougg865.bloomberto.api.BloombertoApi;
import com.dougg865.bloomberto.api.config.Config;
import com.dougg865.bloomberto.api.config.ConfigBuilder;
import com.dougg865.bloomberto.api.config.ConfigValue;
import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;

/**
 * Integración opcional con Bloomberto (lado común). Solo se compila si existe el jar de Bloomberto para el target
 * (ver {@code scripts/fetch-bloomberto.sh}) y solo se carga si Bloomberto está instalado ({@code Compat}).
 *
 * <p>Usa: {@code ConfigBuilder} (config/stormroot.toml y botón "config" en Forge/NeoForge). En el cliente,
 * {@link BloombertoClientCompat} añade efectos de pantalla, temblor de cámara e indicador del clima.
 */
public final class BloombertoCompat {
    /** Nivel de API de Bloomberto con el que se ha probado. */
    public static final int API_LEVEL = 2;

    private static Config config;

    private BloombertoCompat() {
    }

    /** La config de Stormroot en Bloomberto ({@code null} hasta {@link #init}). */
    public static Config config() {
        return config;
    }

    /** @return {@code true} si la integración se ha activado */
    public static boolean init(boolean client) {
        if (!BloombertoApi.has(API_LEVEL)) {
            Stormroot.LOGGER.warn("[Stormroot] Bloomberto es demasiado viejo (nivel de API {} < {}); integración desactivada",
                    BloombertoApi.level(), API_LEVEL);
            return false;
        }

        ConfigBuilder b = ConfigBuilder.create(Stormroot.MOD_ID);
        b.push("fronts");
        ConfigValue<Integer> maxFronts = b.defineInt("max_fronts", 8, 0, 64,
                "Máximo de frentes a la vez en el Overworld");
        ConfigValue<Double> spawnChance = b.defineDouble("spawn_chance", 0.05, 0.0, 1.0,
                "Probabilidad de que nazca un frente cada 5 segundos (si hay sitio)");
        b.pop();
        b.push("lightning");
        ConfigValue<Double> crystalChance = b.defineDouble("crystal_chance", 0.25, 0.0, 1.0,
                "Probabilidad de que un rayo sobre arena o piedra deje un Cristal de rayo");
        ConfigValue<Double> rootChance = b.defineDouble("root_chance", 0.35, 0.0, 1.0,
                "Probabilidad de que un rayo sobre piedra, tierra o arena haga crecer una raíz de rayo hacia abajo");
        b.pop();
        b.push("seepage");
        ConfigValue<Boolean> seepage = b.defineBoolean("enabled", true,
                "La lluvia se filtra al subsuelo y las cuevas reaccionan");
        ConfigValue<Boolean> worldChanges = b.defineBoolean("world_changes", true,
                "Permite crear charcos y barro en las cuevas (y deshacerlos al secarse)");
        ConfigValue<Double> seepSpeed = b.defineDouble("speed", 1.0, 0.1, 50.0,
                "Velocidad de la filtración (1 = normal; súbela para probar)");
        ConfigValue<Integer> maxChanges = b.defineInt("max_changes_per_chunk", 48, 0, 256,
                "Máximo de bloques que la filtración puede cambiar en un chunk");
        b.pop();
        b.push("client");
        ConfigValue<Integer> edgeParticles = b.defineInt("edge_particles_per_tick", 6, 0, 64,
                "Partículas por tick en el borde de los frentes cercanos (0 = ninguna)");
        ConfigValue<Boolean> screenEffects = b.defineBoolean("storm_screen_effects", true,
                "Oscurece y desatura la pantalla dentro de tormentas y niebla");
        ConfigValue<Boolean> cameraShake = b.defineBoolean("lightning_camera_shake", true,
                "La cámara tiembla cuando cae un rayo cerca");
        ConfigValue<Boolean> clouds = b.defineBoolean("custom_clouds", true,
                "Nubes de Stormroot en vez de las vanilla (desactívalo si tu shader pack dibuja sus propias nubes)");
        ConfigValue<Boolean> shadows = b.defineBoolean("cloud_shadows", true,
                "Sombra de las nubes de Stormroot en el suelo");
        ConfigValue<Boolean> hud = b.defineBoolean("weather_hud", false,
                "Muestra en pantalla la intensidad de lluvia, tormenta y niebla donde estás");
        b.pop();
        config = b.build();

        StormrootConfig.maxFronts = maxFronts::get;
        StormrootConfig.frontSpawnChance = spawnChance::get;
        StormrootConfig.lightningCrystalChance = crystalChance::get;
        StormrootConfig.lightningRootChance = rootChance::get;
        StormrootConfig.seepageEnabled = seepage::get;
        StormrootConfig.seepageWorldChanges = worldChanges::get;
        StormrootConfig.seepageSpeed = seepSpeed::get;
        StormrootConfig.seepageMaxChangesPerChunk = maxChanges::get;
        StormrootConfig.edgeParticlesPerTick = edgeParticles::get;
        StormrootConfig.stormScreenEffects = screenEffects::get;
        StormrootConfig.lightningCameraShake = cameraShake::get;
        StormrootConfig.weatherHud = hud::get;
        StormrootConfig.customClouds = clouds::get;
        StormrootConfig.cloudShadows = shadows::get;

        if (client) {
            BloombertoClientCompat.init();
        }
        return true;
    }
}
