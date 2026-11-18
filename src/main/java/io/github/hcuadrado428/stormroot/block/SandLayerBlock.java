package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.weather.Sandstorms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Capas de arena de una tormenta de arena: como las de nieve (hasta {@link Sandstorms#MAX_LAYERS} en el juego), pero en
 * cuanto deja de soplar la tormenta el viento se las lleva, una capa en cada tick aleatorio. No dan nada al romperlas.
 */
public class SandLayerBlock extends SnowLayerBlock {
    public SandLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Sandstorms.blowingAt(level, pos)) {
            return;
        }
        int layers = state.getValue(LAYERS);
        level.setBlock(pos, layers > 1 ? state.setValue(LAYERS, layers - 1) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
