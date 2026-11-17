package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Granizo: cae en el núcleo de una de cada tres tormentas ({@link CloudCover#isHailStorm}), donde vanilla haría llover
 * (no en el desierto ni donde nieva) y a cielo abierto.
 * <ul>
 *   <li>Sin casco, quita medio corazón cada pocos segundos.</li>
 *   <li>Los cultivos maduros vuelven dos etapas atrás.</li>
 *   <li>El cristal y los paneles de cristal normales a la intemperie se pueden romper (opción {@code hail_breaks_glass}).</li>
 *   <li>Deja granizo en el suelo (4 = un bloque de hielo).</li>
 * </ul>
 */
public final class Hail {
    private static final int INTERVAL = 20;
    private static final int RANGE = 32;
    private static final int COLUMNS_PER_PLAYER = 8;
    private static final double HURT_CHANCE = 0.35;
    private static final double GLASS_CHANCE = 0.12;
    private static final double DROP_CHANCE = 0.4;

    private static int ticks;

    private Hail() {
    }

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
            BlockPos at = player.blockPosition();
            if (hailing(level, sky, at) && level.canSeeSky(at) && !player.isCreative() && !player.isSpectator()
                    && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && random.nextDouble() < HURT_CHANCE) {
                //? if >=1.21.2 {
                player.hurtServer(level, level.damageSources().generic(), 1.0F);
                //?} else {
                /*player.hurt(level.damageSources().generic(), 1.0F);
                *///?}
            }
            for (int i = 0; i < COLUMNS_PER_PLAYER; i++) {
                int x = player.getBlockX() + random.nextInt(RANGE * 2 + 1) - RANGE;
                int z = player.getBlockZ() + random.nextInt(RANGE * 2 + 1) - RANGE;
                BlockPos column = new BlockPos(x, 0, z);
                if (!level.isLoaded(column)) {
                    continue;
                }
                BlockPos above = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                if (hailing(level, sky, above)) {
                    strike(level, above, random, GLASS_CHANCE, DROP_CHANCE / COLUMNS_PER_PLAYER);
                }
            }
        }
    }

    /** ¿Cae granizo en esa posición? (núcleo de una tormenta de granizo, y ahí vanilla haría llover). */
    public static boolean hailing(ServerLevel level, SkyWeather.Source sky, BlockPos pos) {
        return SkyWeather.rainCloudAt(sky, pos.getX() + 0.5, pos.getZ() + 0.5) == CloudCover.Kind.HAIL
                && BiomeClimate.precipitationAt(level, pos) == Biome.Precipitation.RAIN;
    }

    /**
     * Una piedra de granizo en la columna cuyo primer bloque libre es {@code above}: daña el cultivo maduro o el cristal
     * que haya debajo y, a veces, deja granizo en el suelo.
     */
    public static void strike(ServerLevel level, BlockPos above, RandomSource random, double glassChance, double dropChance) {
        BlockPos top = above.below();
        BlockState state = level.getBlockState(top);
        BlockState crop = level.getBlockState(above);
        if (crop.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(crop)) {
            damageCrop(level, above);
        } else if (state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state)) {
            damageCrop(level, top);
        } else if ((state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE))
                && io.github.hcuadrado428.stormroot.config.StormrootConfig.hailBreaksGlassActive()
                && random.nextDouble() < glassChance) {
            level.destroyBlock(top, false);
        }
        if (random.nextDouble() < dropChance) {
            ItemEntity stone = new ItemEntity(level, above.getX() + 0.5, above.getY() + 0.2, above.getZ() + 0.5,
                    new ItemStack(ModItems.HAILSTONE));
            stone.setDefaultPickUpDelay();
            level.addFreshEntity(stone);
        }
    }

    /** Un cultivo maduro vuelve dos etapas atrás. */
    public static boolean damageCrop(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return false;
        }
        level.setBlock(pos, crop.getStateForAge(Math.max(0, crop.getMaxAge() - 2)), Block.UPDATE_ALL);
        return true;
    }
}
