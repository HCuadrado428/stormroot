package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.network.FrontsSyncPayload;
import io.github.hcuadrado428.stormroot.network.StormrootNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    private FrontTicker() {
    }

    public static void onServerTick(MinecraftServer server) {
        LightningCrystals.tick();
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
        for (WeatherFront front : sim.step(STEP_TICKS / 20.0, anchors)) {
            Stormroot.LOGGER.info("[Stormroot] Nuevo frente: {}", front);
        }
        applyWeather(overworld, sim);
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
            StormrootNetwork.sendFronts(player, new FrontsSyncPayload(controlsWeather, sim.windAngle(), near));
        }
    }

    /** Simulación del Overworld de este servidor; la crea si hace falta (mundo nuevo o recién abierto). */
    public static FrontSimulation simulation(MinecraftServer server) {
        if (server != currentServer || simulation == null) {
            currentServer = server;
            LightningCrystals.clear();
            simulation = new FrontSimulation(server.overworld().getSeed());
            tickCounter = 0;
            FrontSavedData data = FrontSavedData.get(server);
            if (data.restoreInto(simulation)) {
                controlsWeather = data.controlsWeather();
                Stormroot.LOGGER.info("[Stormroot] Cargados {} frente(s) guardados", simulation.fronts().size());
            } else {
                controlsWeather = true;
            }
        }
        return simulation;
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
    }

    public static boolean controlsWeather() {
        return controlsWeather;
    }

    private static void applyWeather(ServerLevel level, FrontSimulation sim) {
        if (!controlsWeather) {
            return;
        }
        FrontType strongest = null;
        for (ServerPlayer player : level.players()) {
            for (WeatherFront front : sim.fronts()) {
                if (front.intensityAt(player.getX(), player.getZ()) < WEATHER_THRESHOLD) {
                    continue;
                }
                if (front.type() == FrontType.THUNDERSTORM) {
                    strongest = FrontType.THUNDERSTORM;
                } else if (front.type() == FrontType.RAIN && strongest != FrontType.THUNDERSTORM) {
                    strongest = FrontType.RAIN;
                }
                // FOG: no existe en vanilla; se verá cuando haya render en el cliente.
            }
        }

        // Duración un poco mayor que un paso: así el ciclo vanilla no cambia el clima por su cuenta,
        // y si Stormroot se desactiva, el clima vanilla vuelve solo en unos segundos.
        int duration = STEP_TICKS * 2;
        if (strongest == FrontType.THUNDERSTORM) {
            level.setWeatherParameters(0, duration, true, true);
        } else if (strongest == FrontType.RAIN) {
            level.setWeatherParameters(0, duration, true, false);
        } else {
            level.setWeatherParameters(duration, 0, false, false);
        }
    }
}
