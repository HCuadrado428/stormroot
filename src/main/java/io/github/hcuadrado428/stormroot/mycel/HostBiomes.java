package io.github.hcuadrado428.stormroot.mycel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Biomas de la red de micelio y de la bruma de esporas: pantanos, manglares y campos de champiñones. Se mira el bioma
 * de la superficie (a la altura {@link #SAMPLE_Y}), no el de la cueva: la red vive <i>bajo</i> esos biomas.
 * Vale para el servidor y el cliente.
 */
public final class HostBiomes {
    public static final int SAMPLE_Y = 64;

    private HostBiomes() {
    }

    public static boolean isHost(Holder<Biome> biome) {
        return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.MUSHROOM_FIELDS);
    }

    /** ¿Hay un bioma de la red en la superficie sobre esa columna? */
    public static boolean isHostColumn(Level level, int x, int z) {
        return isHost(level.getBiome(new BlockPos(x, SAMPLE_Y, z)));
    }
}
