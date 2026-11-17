package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.network.FrontsSyncPayload;
import io.github.hcuadrado428.stormroot.network.StormrootNetwork;
import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import io.github.hcuadrado428.stormroot.selftest.SelfTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;
import io.github.hcuadrado428.stormroot.config.WorldSettings;

/**
 * Puente entre Minecraft y {@link FrontSimulation}. Solo usa clases vanilla:
 * cada loader llama a {@link #onServerTick(MinecraftServer)} al final de cada tick del servidor.
 *
 * <p>Solo el Overworld. El estado se guarda en el mundo con {@link FrontSavedData}.
 *
 * <p>Clima: el clima vanilla es global, así que de momento Stormroot lo pone según el frente más
 * fuerte que toque a algún jugador (tormenta &gt; lluvia &gt; nada). Cuando haya render en el cliente,
 * cada jugador verá el clima de su zona.
 */
public final class FrontTicker {
    /** Cada cuántos ticks se avanza la simulación (100 ticks = 5 s). */
    public static final int STEP_TICKS = 100;
    /** Distancia (más allá del radio) hasta la que se mandan frentes a cada jugador. */
    public static final double SYNC_DISTANCE = 1024;
    /** Intensidad mínima de un frente para que cuente en el clima (los bordes no cuentan). */
    public static final double WEATHER_THRESHOLD = 0.15;

    private static MinecraftServer currentServer;
    private static FrontSimulation simulation;
    private static int tickCounter;
    private static boolean controlsWeather = true;
    /** Stormroot ya ha puesto el clima al menos una vez en esta sesión (para reconocer un /weather posterior). */
    private static boolean appliedOnce;
    /** Alguien cambió el clima con /weather (u otro mod): Stormroot no lo toca hasta que ese clima termine. */
    private static boolean weatherOverridden;

    private FrontTicker() {
    }

    public static void onServerTick(MinecraftServer server) {
        SelfTest.tick(server);
        LightningCrystals.tick();
        LightningRoots.tick();
        if (server == currentServer) {
            DryStorms.tick(server.overworld());
            BlizzardSnow.tick(server.overworld());
            io.github.hcuadrado428.stormroot.seep.ColdCaves.tick(server);
            HeatWaves.tick(server.overworld());
            Hail.tick(server.overworld());
        }
        if (++tickCounter < STEP_TICKS && server == currentServer) {
            return;
        }
        tickCounter = 0;

        FrontSimulation sim = simulation(server);
        ServerLevel overworld = server.overworld();

        List<double[]> anchors = new ArrayList<>();
        for (ServerPlayer player : overworld.players()) {
            anchors.add(new double[] {player.getX(), player.getZ()});
        }

        sim.setMaxFronts(StormrootConfig.maxFronts.getAsInt());
        sim.setSpawnChance(StormrootConfig.frontSpawnChance.getAsDouble());
        for (WeatherFront front : sim.step(STEP_TICKS / 20.0, anchors, (x, z) -> BiomeClimate.zoneAt(overworld, x, z))) {
            Stormroot.LOGGER.info("[Stormroot] Nuevo frente: {}", front);
        }
        applyWeather(overworld, sim);
        SeepageTicker.step(server, sim, STEP_TICKS / 20.0);
        save(server);
    }

    /** Manda a cada jugador del Overworld los frentes que tiene cerca (para el clima por zonas en el cliente). */
    public static void sync(MinecraftServer server) {
        FrontSimulation sim = simulation(server);
        for (ServerPlayer player : server.overworld().players()) {
            List<WeatherFront> near = new ArrayList<>();
            for (WeatherFront front : sim.fronts()) {
                double dx = front.x() - player.getX();
                double dz = front.z() - player.getZ();
                double max = front.radius() + SYNC_DISTANCE;
                if (dx * dx + dz * dz <= max * max) {
                    near.add(front);
                }
            }
            float seep = (float) SeepageTicker.deepAt(server, player.getBlockX(), player.getBlockZ());
            StormrootNetwork.sendFronts(player, new FrontsSyncPayload(controlsWeatherNow(), sim.windAngle(), seep,
                    skySeed(server), near));
        }
    }

    private static long skySeed(MinecraftServer server) {
        return io.github.hcuadrado428.stormroot.sky.SkySeed.fromWorldSeed(server.overworld().getSeed());
    }

    /** Simulación del Overworld de este servidor; la crea si hace falta (mundo nuevo o recién abierto). */
    /** La simulación ya creada de este servidor, o {@code null} (no la crea). */
    public static FrontSimulation simulationIfRunning() {
        return simulation;
    }

    public static FrontSimulation simulation(MinecraftServer server) {
        if (server != currentServer || simulation == null) {
            currentServer = server;
            LightningCrystals.clear();
            LightningRoots.clear();
            simulation = new FrontSimulation(server.overworld().getSeed());
            tickCounter = 0;
            appliedOnce = false;
            weatherOverridden = false;
            WorldSettings.reset();
            FrontSavedData data = FrontSavedData.get(server);
            if (data.restoreInto(simulation)) {
                controlsWeather = data.controlsWeather();
                Stormroot.LOGGER.info("[Stormroot] Cargados {} frente(s) guardados", simulation.fronts().size());
            } else {
                controlsWeather = true;
            }
            registerSky(server);
        }
        return simulation;
    }

    /** Fuente del cielo del servidor para {@link io.github.hcuadrado428.stormroot.sky.SkyWeather} (lluvia solo bajo los nubarrones). */
    private static void registerSky(MinecraftServer server) {
        long seed = io.github.hcuadrado428.stormroot.sky.SkySeed.fromWorldSeed(server.overworld().getSeed());
        FrontSimulation sim = simulation;
        io.github.hcuadrado428.stormroot.sky.SkyWeather.setServer(Thread.currentThread(),
                new io.github.hcuadrado428.stormroot.sky.SkyWeather.Source() {
                    @Override
                    public boolean active() {
                        return controlsWeatherNow();
                    }

                    @Override
                    public long seed() {
                        return seed;
                    }

                    @Override
                    public double windAngle() {
                        return sim.windAngle();
                    }

                    @Override
                    public double seconds() {
                        return server.overworld().getGameTime() / 20.0;
                    }

                    @Override
                    public List<WeatherFront> fronts() {
                        return sim.fronts();
                    }
                });
    }

    /**
     * Copia el estado de la simulación al SavedData del mundo (se escribe a disco al guardar el mundo)
     * y lo manda a los clientes.
     */
    public static void save(MinecraftServer server) {
        FrontSavedData.get(server).capture(simulation(server), controlsWeather);
        sync(server);
    }

    /** Recalcula el clima ya (p. ej. después de un comando), sin esperar al siguiente paso. */
    public static void applyNow(MinecraftServer server) {
        applyWeather(server.overworld(), simulation(server));
    }

    public static void setControlsWeather(boolean enabled) {
        controlsWeather = enabled;
        // Al volver a activarlo, Stormroot toma el control aunque el clima vanilla tenga tiempos largos.
        appliedOnce = false;
        weatherOverridden = false;
    }

    /** {@code true} si Stormroot está poniendo el clima ahora mismo (activado y sin un /weather encima). */
    public static boolean controlsWeatherNow() {
        return controlsWeather && !weatherOverridden;
    }

    /** {@code true} si hay un /weather (u otro mod) mandando y Stormroot está esperando. */
    public static boolean weatherOverridden() {
        return weatherOverridden;
    }

    public static boolean controlsWeather() {
        return controlsWeather;
    }

    private static void applyWeather(ServerLevel level, FrontSimulation sim) {
        if (!controlsWeather) {
            return;
        }
        // Stormroot siempre pone duraciones de STEP_TICKS * 2 como mucho. Si el mundo tiene una duración mayor,
        // alguien ha usado /weather (u otro mod ha cambiado el clima): se respeta hasta que se acabe.
        int duration = STEP_TICKS * 2;
        ServerLevelData data = level.getServer().getWorldData().overworldData();
        boolean external = data.getClearWeatherTime() > duration || data.getRainTime() > duration
                || data.getThunderTime() > duration;
        if (appliedOnce && external) {
            if (!weatherOverridden) {
                Stormroot.LOGGER.info("[Stormroot] El clima se cambió con /weather: Stormroot espera a que termine");
            }
            weatherOverridden = true;
            return;
        }
        weatherOverridden = false;
        appliedOnce = true;
        FrontType strongest = null;
        for (ServerPlayer player : level.players()) {
            for (WeatherFront front : sim.fronts()) {
                if (front.intensityAt(player.getX(), player.getZ()) < WEATHER_THRESHOLD) {
                    continue;
                }
                if (front.type() == FrontType.THUNDERSTORM) {
                    strongest = FrontType.THUNDERSTORM;
                } else if ((front.type() == FrontType.RAIN || front.type() == FrontType.BLIZZARD)
                        && strongest != FrontType.THUNDERSTORM) {
                    // Ventisca: para vanilla es "lluvia" (en los biomas fríos, nieve).
                    strongest = FrontType.RAIN;
                }
                // FOG: no existe en vanilla; se verá cuando haya render en el cliente.
            }
        }

        // Duración un poco mayor que un paso: así el ciclo vanilla no cambia el clima por su cuenta,
        // y si Stormroot se desactiva, el clima vanilla vuelve solo en unos segundos.
        if (strongest == FrontType.THUNDERSTORM) {
            level.setWeatherParameters(0, duration, true, true);
        } else if (strongest == FrontType.RAIN) {
            level.setWeatherParameters(0, duration, true, false);
        } else {
            level.setWeatherParameters(duration, 0, false, false);
        }
    }
}
