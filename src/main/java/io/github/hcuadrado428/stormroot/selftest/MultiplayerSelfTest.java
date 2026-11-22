package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prueba multijugador en un servidor dedicado ({@code -Dstormroot.selfTest=mp}, ver {@code scripts/selftest-mp.sh}):
 * espera a dos jugadores (Alba y Berto, cada uno con {@code -Dstormroot.selfTest=mp-client}), pone a Alba bajo una
 * tormenta y a Berto lejos con el cielo despejado, y luego lanza {@code /weather rain}, que debe poner un frente
 * encima de cada uno. Los clientes comprueban lo suyo ({@code client/MultiplayerClientSelfTest}). Al irse los dos,
 * escribe {@code [selftest] mp server done} y cierra el servidor.
 */
public final class MultiplayerSelfTest {
    private static final boolean ON = "mp".equals(System.getProperty("stormroot.selfTest", ""));
    /** Alba lejos del punto de aparición (así "estar en su sitio" = el servidor ya montó la escena), Berto más lejos. */
    public static final int ALBA_X = -1500;
    public static final int BERTO_X = 3000;
    public static final int Z = 8;
    private static final int TIMEOUT = 20 * 60 * 8;

    private static int ticks;
    private static int stage;
    private static int stageStart;
    private static boolean done;

    private MultiplayerSelfTest() {
    }

    public static void tick(MinecraftServer server) {
        if (!ON || done) {
            return;
        }
        ticks++;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int waited = ticks - stageStart;
        switch (stage) {
            case 0 -> {
                if (players.size() >= 2) {
                    SelfTest.check("two players join the dedicated server", true, "");
                    cmd(server, "time set 6000");
                    cmd(server, "tp Alba " + ALBA_X + " -60 " + Z);
                    cmd(server, "tp Berto " + BERTO_X + " -60 " + Z);
                    FrontTicker.pauseSpawnsForTest(server.overworld(), TIMEOUT * 4);
                    FrontSimulation sim = FrontTicker.simulation(server);
                    sim.clear();
                    sim.add(new WeatherFront(901, FrontType.THUNDERSTORM, ALBA_X, Z, 120, 0, 0, 1000, 4000));
                    FrontTicker.applyNow(server);
                    FrontTicker.save(server);
                    next();
                } else if (ticks > TIMEOUT) {
                    SelfTest.check("two players join the dedicated server", false, "jugadores=" + players.size());
                    finish(server);
                }
            }
            case 1 -> {
                if (waited < 200) {
                    return;
                }
                // En el hilo del servidor: cada jugador tiene su cielo.
                CloudCover.Kind alba = SkyWeather.cloudKindAt(ALBA_X, Z);
                CloudCover.Kind berto = SkyWeather.cloudKindAt(BERTO_X, Z);
                SelfTest.check("server: storm over Alba, clear over Berto",
                        alba != null && alba != CloudCover.Kind.NONE && berto == CloudCover.Kind.NONE,
                        "Alba=" + alba + " Berto=" + berto);
                next();
            }
            case 2 -> {
                // Los clientes miran su cielo durante este rato; luego /weather rain para los dos.
                if (waited < 600) {
                    return;
                }
                FrontTicker.resumeSpawnsForTest(); // el propio /weather rain tambien quita la pausa; por claridad
                cmd(server, "weather rain 1200");
                next();
            }
            case 3 -> {
                if (waited < 100) {
                    return;
                }
                CloudCover.Kind berto = SkyWeather.cloudKindAt(BERTO_X, Z);
                int fronts = FrontTicker.simulation(server).fronts().size();
                SelfTest.check("server: /weather rain puts a front over each player",
                        fronts >= 2 && berto != null && berto != CloudCover.Kind.NONE,
                        "frentes=" + fronts + " Berto=" + berto);
                next();
            }
            case 4 -> {
                // Se espera a que los clientes terminen y se vayan.
                if (players.isEmpty() && waited > 20) {
                    SelfTest.check("players leave cleanly", true, "");
                    finish(server);
                } else if (waited > TIMEOUT) {
                    SelfTest.check("players leave cleanly", false, "siguen " + players.size());
                    finish(server);
                }
            }
            default -> {
            }
        }
    }

    private static void next() {
        stage++;
        stageStart = ticks;
    }

    private static void cmd(MinecraftServer server, String command) {
        CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, command);
    }

    private static void finish(MinecraftServer server) {
        done = true;
        Stormroot.LOGGER.info("[selftest] mp server done: {} passed, {} failed", SelfTest.passed(), SelfTest.failed());
        server.halt(false);
    }
}
