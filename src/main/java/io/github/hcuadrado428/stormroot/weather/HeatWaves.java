package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.block.StormFireBlock;
import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ola de calor, lo que pasa en la superficie (lo del subsuelo, en {@code seep/SeepageTicker}):
 * <ul>
 *   <li>de día y al sol, el hambre baja más rápido;</li>
 *   <li>a mediodía las hojas secas de los árboles pueden prender (fuego del clima: nunca casas).</li>
 * </ul>
 */
public final class HeatWaves {
    private static final int INTERVAL = 20;
    /** Cansancio extra por segundo al sol con la ola a tope (correr gasta 0,1 por bloque). */
    private static final float EXHAUSTION_PER_SECOND = 0.4f;
    /** Probabilidad por segundo y jugador de que prenda un árbol cerca, con la ola a tope y a mediodía. */
    private static final double LEAF_FIRE_CHANCE = 0.01;
    private static final int FIRE_RANGE = 40;

    private static int ticks;

    private HeatWaves() {
    }

    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        if (!FrontTicker.controlsWeatherNow() || !isDaytime(level)) {
            return;
        }
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            double heat = io.github.hcuadrado428.stormroot.seep.SeepageTicker.intensityAt(sim, FrontType.HEAT_WAVE,
                    player.getX(), player.getZ());
            if (heat < 0.2) {
                continue;
            }
            if (!player.isCreative() && !player.isSpectator() && level.canSeeSky(player.blockPosition())) {
                player.causeFoodExhaustion(EXHAUSTION_PER_SECOND * (float) heat);
            }
            if (random.nextDouble() < LEAF_FIRE_CHANCE * heat * middayFactor(level)) {
                int x = player.getBlockX() + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE;
                int z = player.getBlockZ() + random.nextInt(FIRE_RANGE * 2 + 1) - FIRE_RANGE;
                BlockPos column = new BlockPos(x, 0, z);
                if (level.isLoaded(column)) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                    if (StormFireBlock.fireAllowed(level, top)) {
                        StormFireBlock.igniteNear(level, top, ModBlocks.STORM_FIRE);
                    }
                }
            }
        }
    }

    private static boolean isDaytime(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= 1000 && time < 12000;
    }

    /** 1 a mediodía, 0 al amanecer y al atardecer. */
    private static double middayFactor(ServerLevel level) {
        double time = level.getDayTime() % 24000L;
        return Math.max(0, 1 - Math.abs(time - 6000) / 5000.0);
    }
}
