package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.weather.Frost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hielo de helada: la capa que deja una noche de helada sobre el agua quieta de un bloque de hondo. Se ve como el hielo
 * de vanilla y se rompe igual (vuelve a ser agua), pero en cuanto deja de helar se derrite solo ({@link Frost}).
 */
public class FrostIceBlock extends IceBlock {
    //? if >=1.21 {
    public static final com.mojang.serialization.MapCodec<FrostIceBlock> CODEC = simpleCodec(FrostIceBlock::new);

    @Override
    public com.mojang.serialization.MapCodec<FrostIceBlock> codec() {
        return CODEC;
    }
    //?}

    public FrostIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Frost.freezingAt(level, pos)) {
            level.setBlockAndUpdate(pos, IceBlock.meltsInto());
        }
    }
}
