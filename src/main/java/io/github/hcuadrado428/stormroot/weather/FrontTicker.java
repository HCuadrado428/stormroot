package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.network.FrontsSyncPayload;
import io.github.hcuadrado428.stormroot.network.StormrootNetwork;
import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import io.github.hcuadrado428.stormroot.selftest.SelfTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** Tiempo de juego hasta el que no nacen frentes nuevos (después de un /weather clear). */
    private static long noSpawnUntil = Long.MIN_VALUE;
    /** Frentes que se deshacen por /weather clear o por dormir: ya no ponen lluvia vanilla mientras se van. */
    private static final Set<Long> dissipating = new HashSet<>();
    /** Stormroot está poniendo el clima vanilla ahora mismo (para no confundirlo con un /weather). */
    private static boolean applying;

    private FrontTicker() {
    }

    public static void onServerTick(MinecraftServer server) {
        SelfTest.tick(server);
        io.github.hcuadrado428.stormroot.selftest.MultiplayerSelfTest.tick(server);
        long perfTick = io.github.hcuadrado428.stormroot.Perf.start();
        LightningCrystals.tick();
        LightningRoots.tick();
        if (server == currentServer) {
            DryStorms.tick(server.overworld());
            BlizzardSnow.tick(server.overworld());
            io.github.hcuadrado428.stormroot.seep.ColdCaves.tick(server);
            HeatWaves.tick(server.overworld());
            Hail.tick(server.overworld());
            Monsoons.tick(server.overworld());
            RareSkies.tick(server.overworld());
            io.github.hcuadrado428.stormroot.mycel.SporeMist.tick(server.overworld());
            io.github.hcuadrado428.stormroot.mycel.Mycelium.tick(server.overworld());
            Frost.tick(server.overworld());
            Sandstorms.tick(server.overworld());
            Rainbows.tick(server.overworld());
        }
        io.github.hcuadrado428.stormroot.Perf.end("servidor.tick", perfTick);
        if (++tickCounter < STEP_TICKS && server == currentServer) {
            return;
        }
        tickCounter = 0;
        long perfStep = io.github.hcuadrado428.stormroot.Perf.start();
        step(server);
        io.github.hcuadrado428.stormroot.Perf.end("servidor.paso", perfStep);
    }

    /** Un paso de la simulación (cada {@link #STEP_TICKS}); público para el self-test. */
    public static void step(MinecraftServer server) {
        FrontSimulation sim = simulation(server);
        ServerLevel overworld = server.overworld();
        if (!advancesWeather(overworld)) {
            // Regla doWeatherCycle / advance_weather en false: los frentes se quedan como están (ni se mueven, ni nacen,
            // ni mueren), pero siguen poniendo el clima y mojando.
            applyWeather(overworld, sim);
            stepSeepage(server, sim);
            return;
        }

        List<double[]> anchors = new ArrayList<>();
        for (ServerPlayer player : overworld.players()) {
            anchors.add(new double[] {player.getX(), player.getZ()});
        }

        sim.setMaxFronts(StormrootConfig.maxFronts.getAsInt());
        sim.setSpawnChance(spawnsPaused(overworld) ? 0.0 : StormrootConfig.frontSpawnChance.getAsDouble());
        List<WeatherFront> rainy = new ArrayList<>();
        for (WeatherFront front : sim.fronts()) {
            if (front.type() == FrontType.RAIN || front.type() == FrontType.THUNDERSTORM || front.type() == FrontType.MONSOON) {
                rainy.add(front.copy());
            }
        }
        for (WeatherFront front : sim.step(STEP_TICKS / 20.0, anchors, (x, z) -> BiomeClimate.zoneAt(overworld, x, z))) {
            Stormroot.LOGGER.info("[Stormroot] Nuevo frente: {}", front);
        }
        // Lluvias que se han acabado en este paso: quizá un arcoíris.
        for (WeatherFront ended : rainy) {
            boolean alive = false;
            for (WeatherFront front : sim.fronts()) {
                alive |= front.id() == ended.id();
            }
            if (!alive && controlsWeatherNow()) {
                Rainbows.onRainEnded(overworld, ended);
            }
        }
        dissipating.removeIf(id -> sim.fronts().stream().noneMatch(f -> f.id() == id));
        applyWeather(overworld, sim);
        stepSeepage(server, sim);
        save(server);
    }

    private static void stepSeepage(MinecraftServer server, FrontSimulation sim) {
        long perf = io.github.hcuadrado428.stormroot.Perf.start();
        SeepageTicker.step(server, sim, STEP_TICKS / 20.0);
        io.github.hcuadrado428.stormroot.Perf.end("servidor.paso.filtracion", perf);
    }

    /** {@code false} si la regla del mundo congela el clima ({@code doWeatherCycle}, {@code advance_weather} en 1.21.11). */
    public static boolean advancesWeather(ServerLevel level) {
        //? if >=1.21.11 {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER);
        //?} else {
        /*return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE);
        *///?}
    }

    /** Después de un /weather clear no nacen frentes nuevos hasta que se acabe su duración. */
    public static boolean spawnsPaused(ServerLevel level) {
        return level.getGameTime() < noSpawnUntil;
    }

    public static void resumeSpawnsForTest() {
        noSpawnUntil = Long.MIN_VALUE;
        dissipating.clear();
    }

    /**
     * Lo llama {@code mixin/ServerLevelWeatherMixin} cuando algo pone el clima vanilla ({@code /weather}, otros mods).
     * Las duraciones de Stormroot nunca pasan de {@code STEP_TICKS * 2}; una más larga es un {@code /weather}.
     * Devuelve {@code true} si Stormroot se encarga (y la llamada vanilla se cancela).
     */
    public static boolean onWeatherCommand(ServerLevel level, int clearTime, int weatherTime, boolean rain, boolean thunder) {
        MinecraftServer server = level.getServer();
        if (applying || !controlsWeather || level != server.overworld()
                || Math.max(clearTime, weatherTime) <= STEP_TICKS * 2) {
            return false;
        }
        FrontSimulation sim = simulation(server);
        if (!rain) {
            // /weather clear: los frentes se deshacen poco a poco y no nacen nuevos mientras dure.
            for (WeatherFront front : sim.fronts()) {
                dissipate(front);
            }
            noSpawnUntil = level.getGameTime() + clearTime;
            Stormroot.LOGGER.info("[Stormroot] /weather clear: {} frente(s) se deshacen; sin frentes nuevos en {} ticks",
                    sim.fronts().size(), clearTime);
        } else {
            // /weather rain|thunder: un frente encima de cada jugador (o en el origen si no hay nadie) que dura lo que diga el comando.
            FrontType type = thunder ? FrontType.THUNDERSTORM : FrontType.RAIN;
            double seconds = weatherTime / 20.0;
            noSpawnUntil = Long.MIN_VALUE;
            for (double[] at : weatherAnchors(level)) {
                WeatherFront base = sim.create(type, at[0], at[1]);
                // Nace ya crecido (al 10 % de su vida) para que llueva enseguida, y le quedan `seconds` a partir de ahora.
                double life = seconds / 0.9;
                sim.add(new WeatherFront(base.id(), type, base.x(), base.z(), base.radius(), base.vx(), base.vz(),
                        life * 0.1, life));
            }
            Stormroot.LOGGER.info("[Stormroot] /weather {}: nuevo frente durante {} s", thunder ? "thunder" : "rain", seconds);
        }
        weatherOverridden = false;
        applyNow(server);
        save(server);
        return true;
    }

    /** Lo llama el mixin cuando los jugadores duermen con lluvia: se deshacen los frentes que tienen encima. */
    public static void onSleptThroughWeather(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!controlsWeather || level != server.overworld()) {
            return;
        }
        List<double[]> sleepers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            sleepers.add(new double[] {player.getX(), player.getZ()});
        }
        int n = dissipateNear(server, sleepers);
        Stormroot.LOGGER.info("[Stormroot] Han dormido con mal tiempo: {} frente(s) se deshacen", n);
        applyNow(server);
        save(server);
    }

    /** Deshace los frentes que afectan a alguno de los puntos dados; devuelve cuántos. */
    public static int dissipateNear(MinecraftServer server, List<double[]> points) {
        int n = 0;
        for (WeatherFront front : simulation(server).fronts()) {
            for (double[] p : points) {
                if (front.intensityAt(p[0], p[1]) > 0.0) {
                    dissipate(front);
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    public static boolean isDissipating(WeatherFront front) {
        return dissipating.contains(front.id());
    }

    private static void dissipate(WeatherFront front) {
        front.fadeOut();
        dissipating.add(front.id());
    }

    private static List<double[]> weatherAnchors(ServerLevel level) {
        List<double[]> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            out.add(new double[] {player.getX(), player.getZ()});
        }
        if (out.isEmpty()) {
            out.add(new double[] {0.5, 0.5}); // sin jugadores (servidor vacío, self-test): en el origen
        }
        return out;
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
            List<io.github.hcuadrado428.stormroot.sky.Rainbow> rainbows = new ArrayList<>();
            for (io.github.hcuadrado428.stormroot.sky.Rainbow rainbow : Rainbows.active()) {
                double rx = rainbow.x() - player.getX();
                double rz = rainbow.z() - player.getZ();
                if (rx * rx + rz * rz <= 512 * 512) {
                    rainbows.add(rainbow);
                }
            }
            StormrootNetwork.sendFronts(player, new FrontsSyncPayload(controlsWeatherNow(), sim.windAngle(), seep,
                    skySeed(server), near, rainbows));
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
            noSpawnUntil = Long.MIN_VALUE;
            dissipating.clear();
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
                if (isDissipating(front) || front.intensityAt(player.getX(), player.getZ()) < WEATHER_THRESHOLD) {
                    continue;
                }
                if (front.type() == FrontType.THUNDERSTORM) {
                    strongest = FrontType.THUNDERSTORM;
                } else if ((front.type() == FrontType.RAIN || front.type() == FrontType.BLIZZARD
                        || front.type() == FrontType.MONSOON)
                        && strongest != FrontType.THUNDERSTORM) {
                    // Ventisca: para vanilla es "lluvia" (en los biomas fríos, nieve).
                    strongest = FrontType.RAIN;
                }
                // FOG: no existe en vanilla; se verá cuando haya render en el cliente.
            }
        }

        // Duración un poco mayor que un paso: así el ciclo vanilla no cambia el clima por su cuenta,
        // y si Stormroot se desactiva, el clima vanilla vuelve solo en unos segundos.
        applying = true;
        try {
            if (strongest == FrontType.THUNDERSTORM) {
                level.setWeatherParameters(0, duration, true, true);
            } else if (strongest == FrontType.RAIN) {
                level.setWeatherParameters(0, duration, true, false);
            } else {
                level.setWeatherParameters(duration, 0, false, false);
            }
        } finally {
            applying = false;
        }
    }
}
