package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.sky.SkySeed;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Helada: en las noches despejadas de los biomas fríos y templados ({@link FrostRules}).
 * <ul>
 *   <li>El agua quieta de un solo bloque de hondo y a cielo abierto se congela en {@code frost_ice}, que vuelve a ser
 *       agua cuando deja de helar (sus ticks aleatorios, {@code block/FrostIceBlock}).</li>
 *   <li>Los cultivos no crecen mientras hiela, salvo con luz de antorcha cerca ({@code mixin/CropFrostMixin}).</li>
 * </ul>
 * Solo cuando Stormroot lleva el clima; vale el mismo cálculo en el cliente (la escarcha de {@code ClientWeather}).
 */
public final class Frost {
    private static final int INTERVAL = 40;
    private static final int RANGE = 32;
    private static final int TRIES = 16;

    /** Para el self-test: helar siempre (o nunca) sin mirar la hora ni el cielo. {@code null} = normal. */
    private static Boolean forcedForTest;
    private static int ticks;

    private Frost() {
    }

    public static void forceForTest(Boolean freezing) {
        forcedForTest = freezing;
    }

    /** ¿Hiela en esa columna ahora? */
    public static boolean freezingAt(ServerLevel level, BlockPos pos) {
        if (forcedForTest != null) {
            return forcedForTest;
        }
        if (level.dimension() != Level.OVERWORLD || !FrontTicker.controlsWeatherNow()) {
            return false;
        }
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null) {
            return false;
        }
        return FrostRules.freezing(SkySeed.fromWorldSeed(level.getSeed()), level.getDayTime(),
                BiomeClimate.zoneAt(level, pos.getX(), pos.getZ()), cloudsAt(sim.fronts(), pos.getX(), pos.getZ()));
    }

    /** Intensidad del frente que tapa el cielo en ese punto (la aurora y la tormenta magnética no lo tapan). */
    public static double cloudsAt(List<WeatherFront> fronts, double x, double z) {
        double best = 0;
        for (WeatherFront front : fronts) {
            if (front.type() != FrontType.AURORA && front.type() != FrontType.MAGNETIC_STORM) {
                best = Math.max(best, front.intensityAt(x, z));
            }
        }
        return best;
    }

    /** ¿Se para el cultivo de esa posición? */
    public static boolean stopsGrowth(ServerLevel level, BlockPos pos) {
        return FrostRules.stopsGrowth(freezingAt(level, pos), level.getBrightness(LightLayer.BLOCK, pos));
    }

    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!freezingAt(level, player.blockPosition())) {
                continue;
            }
            // Charcos de cueva cerca de la boca (llega algo de luz del cielo, pero no se ve el cielo).
            for (int i = 0; i < TRIES; i++) {
                BlockPos pos = player.blockPosition().offset(random.nextInt(33) - 16, random.nextInt(17) - 8,
                        random.nextInt(33) - 16);
                if (level.isLoaded(pos)) {
                    freezeCavePuddle(level, pos);
                }
            }
            for (int i = 0; i < TRIES; i++) {
                BlockPos column = player.blockPosition().offset(random.nextInt(RANGE * 2 + 1) - RANGE, 0,
                        random.nextInt(RANGE * 2 + 1) - RANGE);
                if (level.isLoaded(column)) {
                    freeze(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below());
                }
            }
        }
    }

    /** En las cuevas, solo los charcos de la boca: hasta allí llega el frío de la noche. */
    public static final int CAVE_MOUTH_SKY_LIGHT = 6;

    /** Congela un charco poco hondo de una cueva, cerca de la boca (con luz del cielo, pero sin ver el cielo). */
    public static boolean freezeCavePuddle(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (!level.getBlockState(pos).is(Blocks.WATER) || !fluid.isSource()) {
            return false;
        }
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isAir() || level.canSeeSky(above)
                || level.getBrightness(LightLayer.SKY, above) < CAVE_MOUTH_SKY_LIGHT
                || level.getFluidState(pos.below()).isSource() || level.getBlockState(pos.below()).isAir()) {
            return false;
        }
        level.setBlock(pos, ModBlocks.FROST_ICE.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Congela {@code pos} si es agua quieta de un bloque de hondo, con aire encima y a cielo abierto. */
    public static boolean freeze(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (!level.getBlockState(pos).is(Blocks.WATER) || !fluid.isSource()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).isAir() || !level.canSeeSky(pos.above())
                || level.getFluidState(pos.below()).isSource() || level.getBlockState(pos.below()).isAir()) {
            return false;
        }
        level.setBlock(pos, ModBlocks.FROST_ICE.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }
}
