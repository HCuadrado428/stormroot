package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.Stormroot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Puente entre Minecraft y {@link FrontSimulation}. Solo usa clases vanilla:
 * cada loader llama a {@link #onServerTick(MinecraftServer)} al final de cada tick del servidor.
 *
 * <p>MVP: solo el Overworld. Los frentes se guardan en su {@link FrontSavedData}, que se marca como
 * modificado en cada paso y en cada comando que cambie algo ({@link #markDirty}).
 *
 * <p>Clima: el clima vanilla es global, así que de momento Stormroot lo pone según el frente más
 * fuerte que toque a algún jugador (tormenta &gt; lluvia &gt; nada). Cuando haya render en el cliente,
 * cada jugador verá el clima de su zona.
 */
public final class FrontTicker {
    /** Cada cuántos ticks se avanza la simulación (100 ticks = 5 s). */
    public static final int STEP_TICKS = 100;
    /** Intensidad mínima de un frente para que cuente en el clima (los bordes no cuentan). */
    public static final double WEATHER_THRESHOLD = 0.15;

    private static MinecraftServer currentServer;
    private static FrontSavedData data;
    private static int tickCounter;

    private FrontTicker() {
    }

    public static void onServerTick(MinecraftServer server) {
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

        for (WeatherFront front : sim.step(STEP_TICKS / 20.0, anchors)) {
            Stormroot.LOGGER.info("[Stormroot] Nuevo frente: {}", front);
        }
        data.setDirty();
        applyWeather(overworld, sim);
    }

    /**
     * Simulación del Overworld de este servidor. Al abrir un mundo la carga de su {@link FrontSavedData}
     * (o la crea vacía si es un mundo nuevo).
     */
    public static FrontSimulation simulation(MinecraftServer server) {
        return data(server).simulation();
    }

    /** Marca los datos como modificados para que se guarden con el mundo (llamar tras cambiar algo). */
    public static void markDirty(MinecraftServer server) {
        data(server).setDirty();
    }

    private static FrontSavedData data(MinecraftServer server) {
        if (server != currentServer || data == null) {
            ServerLevel overworld = server.overworld();
            FrontSavedData loaded = FrontSavedData.get(overworld);
            if (loaded.simulation() == null) {
                loaded.attach(new FrontSimulation(overworld.getSeed()));
                Stormroot.LOGGER.info("[Stormroot] Frentes cargados: {}", loaded.simulation().fronts().size());
            }
            currentServer = server;
            data = loaded;
            tickCounter = 0;
        }
        return data;
    }

    /** Recalcula el clima ya (p. ej. después de un comando), sin esperar al siguiente paso. */
    public static void applyNow(MinecraftServer server) {
        applyWeather(server.overworld(), simulation(server));
    }

    /** Activa o desactiva el control del clima vanilla; se guarda con el mundo. */
    public static void setControlsWeather(MinecraftServer server, boolean enabled) {
        data(server).setControlsWeather(enabled);
    }

    public static boolean controlsWeather(MinecraftServer server) {
        return data(server).controlsWeather();
    }

    private static void applyWeather(ServerLevel level, FrontSimulation sim) {
        if (!data.controlsWeather()) {
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
