package io.github.hcuadrado428.stormroot.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Puente vanilla: a qué {@link ClimateZone} pertenece un punto del mundo, y si es un bioma sin lluvia (tormenta seca).
 * {@code getBiome} no carga chunks: fuera de los cargados lo calcula la fuente de biomas.
 */
public final class BiomeClimate {
    /** Altura a la que se mira el bioma (los biomas de cueva no cuentan para el clima). */
    private static final int SAMPLE_Y = 64;
    /** Temperatura base por debajo de la cual nieva (la de vanilla). */
    private static final float SNOW_BELOW = 0.15f;

    private BiomeClimate() {
    }

    public static ClimateZone zoneAt(net.minecraft.world.level.Level level, double x, double z) {
        Holder<Biome> holder = level.getBiome(BlockPos.containing(x, SAMPLE_Y, z));
        Biome biome = holder.value();
        if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_BEACH)) {
            return ClimateZone.OCEAN;
        }
        if (!biome.hasPrecipitation()) {
            return ClimateZone.DRY;
        }
        if (biome.getBaseTemperature() < SNOW_BELOW) {
            return ClimateZone.COLD;
        }
        if (holder.is(BiomeTags.IS_MOUNTAIN)) {
            return ClimateZone.MOUNTAIN;
        }
        if (holder.is(BiomeTags.IS_JUNGLE) || holder.is(Biomes.SWAMP) || holder.is(Biomes.MANGROVE_SWAMP)
                || holder.is(BiomeTags.HAS_RUINED_PORTAL_SWAMP)) {
            return ClimateZone.HUMID;
        }
        return ClimateZone.TEMPERATE;
    }

    /** Lo que cae en esa posición ahora (vanilla + Stormroot: fuera de un nubarrón, nada). */
    public static Biome.Precipitation precipitationAt(ServerLevel level, BlockPos pos) {
        //? if >=1.21.11 {
        return level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel());
        //?} else {
        /*return level.getBiome(pos).value().getPrecipitationAt(pos);
        *///?}
    }

    /** Bioma sin lluvia en vanilla en esa posición (desierto, badlands, sabana...): ahí las tormentas son secas. */
    public static boolean isDry(ServerLevel level, BlockPos pos) {
        return !level.getBiome(pos).value().hasPrecipitation();
    }
}
