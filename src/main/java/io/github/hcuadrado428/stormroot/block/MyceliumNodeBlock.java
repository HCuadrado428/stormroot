package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nodo de la red de micelio: el corazón de una parte de la red. Brilla un poco, suelta esporas y hace crecer hilos a su
 * alrededor. Si se rompe, los hilos que solo estaban unidos a él mueren. Da esporas y setas (el nodo, con toque de seda).
 */
public class MyceliumNodeBlock extends Block {
    public MyceliumNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Mycelium.nodeTick(level, pos, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(), 0, 0.01, 0);
        }
    }
}
