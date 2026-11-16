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
 * <p>MVP: solo el Overworld y los frentes viven en memoria (se pierden al cerrar el mundo).
 * Siguiente paso: guardarlos con SavedData (la API cambia en 1.20.2 y 1.21.5; ver skill version-matrix).
 */
public final class FrontTicker {
    /** Cada cuántos ticks se avanza la simulación (100 ticks = 5 s). */
    public static final int STEP_TICKS = 100;

    private static MinecraftServer currentServer;
    private static FrontSimulation simulation;
    private static int tickCounter;

    private FrontTicker() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (server != currentServer) {
            // Mundo nuevo (o el primero): empezamos una simulación limpia.
            currentServer = server;
            simulation = null;
            tickCounter = 0;
        }
        if (++tickCounter < STEP_TICKS) {
            return;
        }
        tickCounter = 0;

        ServerLevel overworld = server.overworld();
        if (simulation == null) {
            simulation = new FrontSimulation(overworld.getSeed());
        }

        List<double[]> anchors = new ArrayList<>();
        for (ServerPlayer player : overworld.players()) {
            anchors.add(new double[] {player.getX(), player.getZ()});
        }

        for (WeatherFront front : simulation.step(STEP_TICKS / 20.0, anchors)) {
            Stormroot.LOGGER.info("[Stormroot] Nuevo frente: {}", front);
        }
    }

    /** Simulación activa del Overworld, o {@code null} si el servidor aún no ha hecho ningún paso. */
    public static FrontSimulation simulation() {
        return simulation;
    }
}
