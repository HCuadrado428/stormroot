package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.selftest.MultiplayerSelfTest;
import io.github.hcuadrado428.stormroot.selftest.SelfTest;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * La parte de cada jugador de la prueba multijugador ({@code -Dstormroot.selfTest=mp-client}; el servidor dedicado
 * lleva {@code selftest/MultiplayerSelfTest}). Alba debe ver tormenta encima y Berto cielo despejado; tras el
 * {@code /weather rain} del servidor, los dos ven lluvia. Escribe {@code [selftest] mp client done} y cierra el juego.
 */
public final class MultiplayerClientSelfTest {
    private static final boolean ON = "mp-client".equals(System.getProperty("stormroot.selfTest", ""));
    private static final int TIMEOUT = 20 * 60 * 6;

    private static int ticks;
    private static int stage;
    private static int stageStart;
    private static String pendingShot;
    private static boolean done;

    private MultiplayerClientSelfTest() {
    }

    public static void tick() {
        if (!ON || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ticks++;
        if (pendingShot != null) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> { });
            Stormroot.LOGGER.info("[selftest] SCREENSHOT {}", pendingShot);
            pendingShot = null;
            return;
        }
        String name = mc.player.getName().getString();
        boolean alba = name.equals("Alba");
        String who = name.toLowerCase(Locale.ROOT);
        double x = mc.player.getX();
        double z = mc.player.getZ();
        int expectedX = alba ? MultiplayerSelfTest.ALBA_X : MultiplayerSelfTest.BERTO_X;
        boolean inPlace = Math.abs(x - expectedX) < 50;
        int fronts = ClientFronts.snapshot().size();
        int waited = ticks - stageStart;
        CloudCover.Kind kind = SkyWeather.cloudKindAt(x, z);
        switch (stage) {
            case 0 -> {
                // Hasta que llegan el teletransporte y la primera sincronización (ClientFronts.active()).
                if (inPlace && ClientFronts.active()) {
                    if (stageStart == 0) {
                        stageStart = ticks;
                    }
                    if (ticks - stageStart < 160) {
                        mc.player.setXRot(-50f);
                        return;
                    }
                    boolean storm = ClientFronts.snapshot().stream().anyMatch(f -> f.id() == 901);
                    if (alba) {
                        SelfTest.report("client alba: the storm front arrives from the dedicated server", storm, "frentes=" + fronts);
                    } else {
                        // La sincronización es por zonas: a 3000 bloques el frente de Alba NO debe llegarle.
                        SelfTest.report("client berto: far fronts are not synced", !storm && fronts == 0, "frentes=" + fronts);
                    }
                    boolean ok = alba ? kind != null && kind != CloudCover.Kind.NONE : kind == CloudCover.Kind.NONE;
                    SelfTest.report("client " + who + ": sees its own sky", ok, "nube encima=" + kind);
                    pendingShot = "mp_" + who + "_cielo";
                    next();
                } else if (ticks > TIMEOUT) {
                    SelfTest.report("client " + who + ": fronts arrive from the dedicated server", false,
                            "frentes=" + fronts + " x=" + (int) x + " sync=" + ClientFronts.active());
                    finish(mc);
                }
            }
            case 1 -> {
                // El servidor lanza /weather rain: un frente nuevo encima de cada uno.
                boolean rainHere = ClientFronts.snapshot().stream().anyMatch(f -> f.type() == io.github.hcuadrado428.stormroot.weather.FrontType.RAIN
                        && Math.hypot(f.x() - x, f.z() - z) < 60);
                if (rainHere && kind != null && kind != CloudCover.Kind.NONE && waited > 100) {
                    SelfTest.report("client " + who + ": /weather rain reaches every player", true, "");
                    pendingShot = "mp_" + who + "_lluvia";
                    next();
                } else if (waited > TIMEOUT) {
                    SelfTest.report("client " + who + ": /weather rain reaches every player", false,
                            "lluvia encima=" + rainHere + " nube encima=" + kind + " frentes=" + fronts);
                    finish(mc);
                }
            }
            default -> {
                if (waited > 40) {
                    finish(mc);
                }
            }
        }
    }

    private static void next() {
        stage++;
        stageStart = ticks;
    }

    private static void finish(Minecraft mc) {
        done = true;
        Stormroot.LOGGER.info("[selftest] mp client done: {} passed, {} failed", SelfTest.passed(), SelfTest.failed());
        mc.stop();
    }
}
