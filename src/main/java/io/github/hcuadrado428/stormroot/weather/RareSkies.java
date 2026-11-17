package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fenómenos raros, lo que pasa en el servidor:
 * <ul>
 *   <li><b>Aurora</b> (de noche): a veces cae polvo de aurora cerca del jugador, a cielo abierto.</li>
 *   <li><b>Tormenta magnética</b>: el mineral de hierro de alrededor se magnetiza ({@code magnetic_rock}, suelta hierro
 *       en bruto igual); los instrumentos dan lecturas locas ({@code InstrumentBlock}).</li>
 * </ul>
 */
public final class RareSkies {
    private static final int INTERVAL = 20;
    private static final double DUST_CHANCE = 0.05;
    private static final int MAGNET_RANGE = 16;
    private static final int MAGNET_TRIES = 24;

    private static int ticks;

    private RareSkies() {
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
        boolean night = isNight(level);
        for (ServerPlayer player : level.players()) {
            double aurora = io.github.hcuadrado428.stormroot.seep.SeepageTicker.intensityAt(sim, FrontType.AURORA, player.getX(), player.getZ());
            if (night && aurora > 0.3 && random.nextDouble() < DUST_CHANCE * aurora) {
                int x = player.getBlockX() + random.nextInt(17) - 8;
                int z = player.getBlockZ() + random.nextInt(17) - 8;
                BlockPos column = new BlockPos(x, 0, z);
                if (level.isLoaded(column)) {
                    dropDust(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column));
                }
            }
            double magnetic = io.github.hcuadrado428.stormroot.seep.SeepageTicker.intensityAt(sim, FrontType.MAGNETIC_STORM,
                    player.getX(), player.getZ());
            if (magnetic > 0.3) {
                for (int i = 0; i < MAGNET_TRIES; i++) {
                    BlockPos pos = player.blockPosition().offset(random.nextInt(MAGNET_RANGE * 2 + 1) - MAGNET_RANGE,
                            random.nextInt(MAGNET_RANGE * 2 + 1) - MAGNET_RANGE, random.nextInt(MAGNET_RANGE * 2 + 1) - MAGNET_RANGE);
                    if (level.isLoaded(pos)) {
                        magnetize(level, pos);
                    }
                }
            }
        }
    }

    /** Polvo de aurora que cae del cielo en {@code above}. */
    public static void dropDust(ServerLevel level, BlockPos above) {
        ItemEntity dust = new ItemEntity(level, above.getX() + 0.5, above.getY() + 0.5, above.getZ() + 0.5,
                new ItemStack(ModItems.AURORA_DUST));
        dust.setDefaultPickUpDelay();
        level.addFreshEntity(dust);
    }

    /** Mineral de hierro → roca magnetizada (para siempre: sigue soltando hierro en bruto). */
    public static boolean magnetize(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            level.setBlock(pos, ModBlocks.MAGNETIC_ROCK.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private static boolean isNight(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= 13000 && time < 23000;
    }
}
