package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hilo de la red de micelio: una capa fina pegada al suelo, las paredes o el techo (como las venas de sculk). Usa el
 * mismo bloque base que el liquen luminoso (caras por dirección, se puede anegar), sin luz propia.
 * En cada tick aleatorio: con luz cerca se retira; si ya no está unido a ningún nodo, muere; si no, crece
 * ({@link Mycelium#veinTick}).
 */
public class MyceliumVeinBlock extends GlowLichenBlock {
    public MyceliumVeinBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Mycelium.veinTick(level, pos, random);
    }
}
