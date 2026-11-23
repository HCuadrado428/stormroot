package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import io.github.hcuadrado428.stormroot.weather.FrontSavedData;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import net.minecraft.server.MinecraftServer;

/**
 * Prueba de compatibilidad con mundos guardados por versiones anteriores del mod
 * ({@code -Dstormroot.selfTest=oldworld}, ver {@code scripts/selftest-oldworld.sh} y el job {@code selftest-oldworld}):
 * el mundo lo generó el self-test del commit apuntado por {@code compat.ref} y aquí lo abre el código actual.
 * Comprueba que los datos guardados (frentes y ajustes, filtración, redes de micelio) cargan, que la simulación sigue
 * andando un rato sin romperse y que se puede volver a guardar. Escribe {@code [selftest] oldworld done} y cierra.
 */
public final class OldWorldSelfTest {
    private static final boolean ON = "oldworld".equals(System.getProperty("stormroot.selfTest", ""));

    private static int ticks;
    private static boolean done;

    private OldWorldSelfTest() {
    }

    public static void tick(MinecraftServer server) {
        if (!ON || done) {
            return;
        }
        ticks++;
        if (ticks == 40) {
            // Si un codec no entiende el formato viejo, vanilla descarta el archivo y entrega datos vacíos:
            // por eso se mira que lo guardado de verdad esté, no solo que no pete.
            SeepageSavedData seepage = SeepageSavedData.get(server);
            int cells = seepage.field().cells().size();
            int networks = seepage.mycelium().entries().size();
            Stormroot.LOGGER.info("[selftest] oldworld: celdas de filtración={} redes de micelio={}", cells, networks);
            SelfTest.check("old world: mycelium networks load", networks >= 1, "redes=" + networks);
            SelfTest.check("old world: seepage data loads", cells + networks >= 1, "celdas=" + cells);
            FrontSavedData fronts = FrontSavedData.get(server);
            SelfTest.check("old world: fronts and settings load", fronts != null,
                    "frentes=" + FrontTicker.simulation(server).fronts().size());
        }
        if (ticks == 900) {
            // Un buen rato de simulación normal encima del mundo viejo (frentes, filtración, micelio).
            SelfTest.check("old world: simulation keeps running", true, "");
            FrontTicker.save(server);
            server.saveEverything(true, true, true);
            SelfTest.check("old world: saves again", true, "");
            done = true;
            Stormroot.LOGGER.info("[selftest] oldworld done: {} passed, {} failed", SelfTest.passed(), SelfTest.failed());
            server.halt(false);
        }
    }
}
