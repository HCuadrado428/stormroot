package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Nieve de la ventisca: bajo su nubarrón, donde vanilla haría nevar, la nieve se acumula de verdad (hasta
 * {@link #MAX_LAYERS} capas). Fuera de las ventiscas, las capas de más se van derritiendo poco a poco (vanilla solo deja
 * una capa).
 */
public final class BlizzardSnow {
    public static final int MAX_LAYERS = 4;
    private static final int INTERVAL = 10;
    private static final int RANGE = 48;
    private static final int COLUMNS_PER_PLAYER = 6;

    private static int ticks;

    private BlizzardSnow() {
    }

    /** Lo llama {@link FrontTicker} cada tick del servidor. */
    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        SkyWeather.Source sky = SkyWeather.serverSource();
        if (sky == null || !FrontTicker.controlsWeatherNow()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < COLUMNS_PER_PLAYER; i++) {
                int x = player.getBlockX() + random.nextInt(RANGE * 2 + 1) - RANGE;
                int z = player.getBlockZ() + random.nextInt(RANGE * 2 + 1) - RANGE;
                BlockPos column = new BlockPos(x, 0, z);
                if (!level.isLoaded(column)) {
                    continue;
                }
                BlockPos above = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column);
                boolean underBlizzard = SkyWeather.rainCloudAt(sky, x + 0.5, z + 0.5) == CloudCover.Kind.SNOW
                        && BiomeClimate.precipitationAt(level, above) == Biome.Precipitation.SNOW;
                if (underBlizzard) {
                    addLayer(level, above);
                } else if (random.nextInt(4) == 0) {
                    melt(level, above);
                }
            }
        }
    }

    /**
     * Una capa más de nieve en la columna cuya superficie está en {@code above} (el primer bloque de aire).
     *
     * @return {@code true} si cambió algo
     */
    public static boolean addLayer(ServerLevel level, BlockPos above) {
        BlockPos top = level.getBlockState(above).is(Blocks.SNOW) ? above : above.below();
        above = top.above();
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers >= MAX_LAYERS) {
                return false;
            }
            level.setBlock(top, state.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            return true;
        }
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (level.getBlockState(above).isAir() && snow.canSurvive(level, above)) {
            level.setBlock(above, snow, Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** Quita una capa de las que pasan de una (la que deja vanilla se queda). */
    public static boolean melt(ServerLevel level, BlockPos above) {
        BlockPos top = level.getBlockState(above).is(Blocks.SNOW) ? above : above.below();
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) > 1) {
            level.setBlock(top, state.setValue(SnowLayerBlock.LAYERS, state.getValue(SnowLayerBlock.LAYERS) - 1), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }
}
