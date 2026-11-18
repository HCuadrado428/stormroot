package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Tormenta de arena ({@link FrontType#SANDSTORM}, solo en biomas secos):
 * <ul>
 *   <li>Deja capas de arena ({@code block/SandLayerBlock}, hasta {@link #MAX_LAYERS}) sobre arena y terracota natural a
 *       cielo abierto; el viento se las lleva cuando acaba. Nunca sobre cultivos, madera ni bloques de construcción.</li>
 *   <li>En el centro, si miras contra el viento sin gafas de arena, ceguera breve de vez en cuando.</li>
 *   <li>La visión corta, la arena que vuela y la calima las pone el cliente; el viento ya desvía las flechas.</li>
 * </ul>
 */
public final class Sandstorms {
    public static final int MAX_LAYERS = 3;
    /** Intensidad a partir de la cual la tormenta deja arena y es "el centro" (ceguera). */
    public static final double LAYER_AT = 0.2;
    public static final double CORE_AT = 0.6;
    /** Probabilidad por segundo de ceguera en el centro mirando contra el viento. */
    public static final double BLIND_CHANCE = 0.12;
    public static final int BLIND_TICKS = 50;
    /** Probabilidad por segundo, en el centro de la tormenta, de que el viento destape un fósil cerca. */
    public static final double FOSSIL_CHANCE = 0.003;
    private static final int INTERVAL = 20;
    private static final int RANGE = 24;
    private static final int TRIES = 12;

    /** Para el self-test: soplar siempre o nunca. {@code null} = normal. */
    private static Boolean forcedForTest;
    private static int ticks;

    private Sandstorms() {
    }

    public static void forceForTest(Boolean blowing) {
        forcedForTest = blowing;
    }

    public static double intensityAt(ServerLevel level, BlockPos pos) {
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null || !FrontTicker.controlsWeatherNow() || !BiomeClimate.isDry(level, pos)) {
            return 0;
        }
        return SeepageTicker.intensityAt(sim, FrontType.SANDSTORM, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** ¿Sigue soplando la tormenta de arena aquí? (si no, las capas se van). */
    public static boolean blowingAt(ServerLevel level, BlockPos pos) {
        if (forcedForTest != null) {
            return forcedForTest;
        }
        return intensityAt(level, pos) >= LAYER_AT;
    }

    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            double intensity = intensityAt(level, player.blockPosition());
            if (intensity < LAYER_AT) {
                continue;
            }
            for (int i = 0; i < TRIES; i++) {
                if (random.nextDouble() > intensity) {
                    continue;
                }
                BlockPos column = player.blockPosition().offset(random.nextInt(RANGE * 2 + 1) - RANGE, 0,
                        random.nextInt(RANGE * 2 + 1) - RANGE);
                if (level.isLoaded(column) && BiomeClimate.isDry(level, column)) {
                    addLayer(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column));
                }
            }
            if (intensity >= CORE_AT && random.nextDouble() < FOSSIL_CHANCE) {
                BlockPos column = player.blockPosition().offset(random.nextInt(49) - 24, 0, random.nextInt(49) - 24);
                if (level.isLoaded(column) && BiomeClimate.isDry(level, column)) {
                    uncoverFossil(level, level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column).below(),
                            random.nextBoolean(), random);
                }
            }
            if (intensity >= CORE_AT && level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
                FrontSimulation sim = FrontTicker.simulationIfRunning();
                double angle = sim == null ? 0 : sim.windAngle();
                if (blinds(intensity, facingWind(player, angle), hasGoggles(player), random.nextDouble())) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0));
                }
            }
        }
    }

    /**
     * Una capa más de arena en {@code pos} (el hueco sobre el suelo, o una capa de arena que ya hay ahí o justo debajo).
     * Solo sobre arena o terracota naturales, a cielo abierto y hasta {@link #MAX_LAYERS}.
     */
    public static boolean addLayer(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        if (below.is(ModBlocks.SAND_LAYER)) {
            pos = pos.below();
            here = below;
            below = level.getBlockState(pos.below());
        }
        if (here.is(ModBlocks.SAND_LAYER)) {
            int layers = here.getValue(SnowLayerBlock.LAYERS);
            if (layers >= MAX_LAYERS) {
                return false;
            }
            level.setBlock(pos, here.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            return true;
        }
        if (!here.isAir() || !level.canSeeSky(pos) || !(below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA))) {
            return false;
        }
        level.setBlock(pos, ModBlocks.SAND_LAYER.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * El viento se lleva la arena de encima de un fósil: una columna de huesos (5–7 bloques) con costillas, a ras del
     * suelo. Solo sustituye arena natural a cielo abierto. Devuelve cuántos bloques de hueso ha puesto.
     */
    public static int uncoverFossil(ServerLevel level, BlockPos surface, boolean alongX, RandomSource random) {
        int length = 5 + random.nextInt(3);
        Direction.Axis spine = alongX ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis rib = alongX ? Direction.Axis.Z : Direction.Axis.X;
        int placed = 0;
        for (int i = 0; i < length; i++) {
            int along = i - length / 2;
            BlockPos p = alongX ? surface.offset(along, 0, 0) : surface.offset(0, 0, along);
            placed += placeBone(level, p, spine) ? 1 : 0;
            if (i % 2 == 1 && i < length - 1) {
                int ribLength = 1 + random.nextInt(2);
                for (int r = 1; r <= ribLength; r++) {
                    BlockPos left = alongX ? p.offset(0, 0, r) : p.offset(r, 0, 0);
                    BlockPos right = alongX ? p.offset(0, 0, -r) : p.offset(-r, 0, 0);
                    placed += placeBone(level, left, rib) ? 1 : 0;
                    placed += placeBone(level, right, rib) ? 1 : 0;
                }
            }
        }
        return placed;
    }

    private static boolean placeBone(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(BlockTags.SAND) || !level.canSeeSky(pos.above())) {
            return false;
        }
        level.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), Block.UPDATE_ALL);
        return true;
    }

    public static boolean hasGoggles(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SAND_GOGGLES);
    }

    /** ¿Mira el jugador contra el viento (de cara a la arena)? */
    public static boolean facingWind(Player player, double windAngle) {
        Vec3 look = player.getLookAngle();
        double toward = -(look.x * WindRules.dirX(windAngle) + look.z * WindRules.dirZ(windAngle));
        return toward > 0.5;
    }

    /** Regla pura de la ceguera: centro de la tormenta, de cara al viento, sin gafas y con suerte. */
    public static boolean blinds(double intensity, boolean facingWind, boolean goggles, double roll) {
        return intensity >= CORE_AT && facingWind && !goggles && roll < BLIND_CHANCE;
    }
}
