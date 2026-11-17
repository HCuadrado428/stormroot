package io.github.hcuadrado428.stormroot.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Monzón en la superficie: los cultivos que reciben la lluvia torrencial crecen más rápido (un "tick aleatorio" de más).
 * Lo de las cuevas y las orillas inundadas va en {@code seep/SeepageTicker}.
 */
public final class Monsoons {
    private static final int INTERVAL = 20;
    private static final int RANGE = 32;
    private static final int COLUMNS_PER_PLAYER = 24;

    private static int ticks;

    private Monsoons() {
    }

    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null || !FrontTicker.controlsWeatherNow()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (io.github.hcuadrado428.stormroot.seep.SeepageTicker.intensityAt(sim, FrontType.MONSOON, player.getX(), player.getZ()) < 0.3) {
                continue;
            }
            for (int i = 0; i < COLUMNS_PER_PLAYER; i++) {
                int x = player.getBlockX() + random.nextInt(RANGE * 2 + 1) - RANGE;
                int z = player.getBlockZ() + random.nextInt(RANGE * 2 + 1) - RANGE;
                BlockPos column = new BlockPos(x, 0, z);
                if (!level.isLoaded(column)) {
                    continue;
                }
                BlockPos above = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                grow(level, above, random);
            }
        }
    }

    /** Un empujón de crecimiento al cultivo de esa posición, si lo hay y le llueve. */
    public static boolean grow(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock) || !level.isRainingAt(pos)) {
            return false;
        }
        state.randomTick(level, pos, random);
        return true;
    }
}
