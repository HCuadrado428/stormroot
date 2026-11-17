package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.compat.Compat;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import io.github.hcuadrado428.stormroot.selftest.SelfTest;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Self-test del CLIENTE (modo {@code client} o {@code all}), en un mundo de un jugador. Espera a que termine el del
 * servidor integrado ({@link SelfTest}) y entonces comprueba con el jugador real:
 * que llegan los frentes, que la tormenta se ve y se va, la niebla, la humedad de las cuevas y (con Bloomberto)
 * los efectos de pantalla y el archivo de config. Al terminar cierra el juego.
 */
public final class ClientSelfTest {
    private static final int TIMEOUT = 600;

    private static int ticks;
    private static int stage;
    private static int stageStart;
    private static boolean done;
    private static long nextFrontId = 10_000;

    private ClientSelfTest() {
    }

    /** Lo llama {@link ClientWeather#tick()} cada tick de cliente. */
    public static void tick() {
        if (!SelfTest.clientEnabled() || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ticks++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SelfTest.report("client is singleplayer", false, "no hay servidor integrado");
            finish(mc);
            return;
        }
        double x = mc.player.getX();
        double z = mc.player.getZ();
        int waited = ticks - stageStart;
        switch (stage) {
            case 0 -> {
                if (ticks > 100 && SelfTest.serverDone()) {
                    Stormroot.LOGGER.info("[selftest] cliente: empezando");
                    spawnAt(server, FrontType.THUNDERSTORM, x, z);
                    next();
                }
            }
            case 1 -> {
                if (ClientFronts.active() && !ClientFronts.snapshot().isEmpty()) {
                    SelfTest.report("client receives fronts", true, "");
                    next();
                } else if (waited > TIMEOUT) {
                    SelfTest.report("client receives fronts", false, "no llegó ningún paquete de frentes");
                    next();
                }
            }
            case 2 -> {
                boolean visible = ClientWeather.thunder() > 0.6f && mc.level.getThunderLevel(1.0f) > 0.5f
                        && mc.level.getRainLevel(1.0f) > 0.5f;
                if (visible || waited > TIMEOUT) {
                    SelfTest.report("storm is visible on the client", visible,
                            "trueno=" + ClientWeather.thunder() + ", lluvia=" + ClientWeather.rain());
                    checkBloomberto(mc);
                    clear(server);
                    next();
                }
            }
            case 3 -> {
                boolean gone = ClientWeather.thunder() < 0.05f && ClientWeather.rain() < 0.05f
                        && mc.level.getRainLevel(1.0f) < 0.1f;
                if (gone || waited > TIMEOUT) {
                    SelfTest.report("storm fades when the front leaves", gone,
                            "trueno=" + ClientWeather.thunder() + ", lluvia=" + ClientWeather.rain());
                    spawnAt(server, FrontType.FOG, x, z);
                    next();
                }
            }
            case 4 -> {
                boolean foggy = ClientWeather.fog() > 0.6f && ClientWeather.fogFarPlane(192f) < 100f
                        && ClientWeather.fogAppliedCount() > 0;
                if (foggy || waited > TIMEOUT) {
                    SelfTest.report("fog bank on the client", foggy, "niebla=" + ClientWeather.fog()
                            + ", veces que el enganche de niebla la aplicó=" + ClientWeather.fogAppliedCount());
                    clear(server);
                    int cx = (int) Math.floor(x) >> 4;
                    int cz = (int) Math.floor(z) >> 4;
                    server.execute(() -> {
                        SeepageSavedData data = SeepageSavedData.get(server);
                        data.field().getOrCreate(cx, cz).set(1);
                        data.changed();
                        FrontTicker.save(server);
                    });
                    next();
                }
            }
            case 5 -> {
                boolean wet = ClientFronts.seep() > 0.9f;
                if (wet || waited > TIMEOUT) {
                    SelfTest.report("cave humidity reaches the client", wet, "humedad=" + ClientFronts.seep());
                    if (Compat.bloomberto()) {
                        // Un frente a ~80 bloques de su borde: debe verse de lejos.
                        spawnAt(server, FrontType.THUNDERSTORM, x + 200, z);
                        next();
                    } else {
                        finish(mc);
                    }
                }
            }
            case 6 -> {
                long drawn = bloombertoLong("distantFrontsDrawn");
                if (drawn > 0 || waited > TIMEOUT) {
                    SelfTest.report("bloomberto distant fronts are drawn", drawn > 0, "no se dibujó ningún frente lejano");
                    clear(server);
                    finish(mc);
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

    private static void spawnAt(MinecraftServer server, FrontType type, double x, double z) {
        long id = nextFrontId++;
        server.execute(() -> {
            FrontSimulation sim = FrontTicker.simulation(server);
            sim.clear();
            // A media vida (intensidad plena) y quieto encima del jugador.
            sim.add(new WeatherFront(id, type, x, z, 120, 0, 0, 1000, 2000));
            FrontTicker.applyNow(server);
            FrontTicker.save(server);
        });
    }

    private static void clear(MinecraftServer server) {
        server.execute(() -> {
            FrontTicker.simulation(server).clear();
            FrontTicker.applyNow(server);
            FrontTicker.save(server);
        });
    }

    private static void checkBloomberto(Minecraft mc) {
        if (!Compat.bloomberto()) {
            Stormroot.LOGGER.info("[selftest] SKIP bloomberto (no está instalado en este target)");
            return;
        }
        boolean config = new File(mc.gameDirectory, "config/" + Stormroot.MOD_ID + ".toml").isFile();
        SelfTest.report("bloomberto config file", config, "no existe config/stormroot.toml");
        try {
            Object active = Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod("effectsActive").invoke(null);
            SelfTest.report("bloomberto storm screen effects", Boolean.TRUE.equals(active),
                    "los efectos de pantalla no se encendieron en la tormenta");
            Object screen = Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod("configScreen", net.minecraft.client.gui.screens.Screen.class).invoke(null, (Object) null);
            SelfTest.report("bloomberto config screen", screen != null, "la pantalla de config no se pudo crear");
        } catch (ReflectiveOperationException e) {
            SelfTest.report("bloomberto storm screen effects", false, e.toString());
        }
    }

    private static long bloombertoLong(String method) {
        try {
            return (Long) Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod(method).invoke(null);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static void finish(Minecraft mc) {
        done = true;
        Stormroot.LOGGER.info("[selftest] client done: {} passed, {} failed", SelfTest.passed(), SelfTest.failed());
        if (SelfTest.exitWhenDone()) {
            mc.stop();
        }
    }
}
