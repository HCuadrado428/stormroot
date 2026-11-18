package io.github.hcuadrado428.stormroot.mycel;

import io.github.hcuadrado428.stormroot.block.MyceliumNodeBlock;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.block.MyceliumVeinBlock;
import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.seep.SeepageCell;
import io.github.hcuadrado428.stormroot.seep.SeepageField;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import io.github.hcuadrado428.stormroot.entity.ModEntities;

/**
 * Red de micelio, lo que pasa en el mundo (las reglas puras están en {@link MyceliumRules}).
 * <ul>
 *   <li><b>Nace</b> en cuevas húmedas bajo pantanos, manglares y campos de champiñones: un nodo en el suelo.</li>
 *   <li><b>Crece</b> en los ticks aleatorios de hilos y nodos: más con humedad, el doble con niebla (esporas), nada con
 *       sequía. Solo por cuevas bajo esos biomas y hasta {@link MyceliumNetworks#MAX_CHUNKS} chunks por red.</li>
 *   <li><b>Se asusta con la luz</b>: los hilos con luz de antorchas se retiran y no crecen hacia ella.</li>
 *   <li><b>Rompe un nodo</b> y los hilos que solo dependían de él mueren.</li>
 * </ul>
 * La red es permanente: no se deshace con el tiempo (solo con luz o sin nodo).
 */
public final class Mycelium {
    private static final int BIRTH_INTERVAL = 40;
    private static final int BIRTH_RANGE = 40;
    private static final int BIRTH_TRIES = 6;
    private static final double BIRTH_CHANCE = 0.02;
    /** Una red nueva no nace a menos de estos chunks de otra. */
    private static final int BIRTH_SPACING_CHUNKS = 4;
    private static final int MIN_DEPTH = 6;
    /** En un tick aleatorio, probabilidad de que un hilo mire si sigue unido a un nodo. */
    private static final double ORPHAN_CHECK = 1.0 / 8;

    /** Para el self-test: crecer también fuera de los biomas de la red y sin techo encima. */
    private static boolean anywhereForTest;
    private static int ticks;

    private Mycelium() {
    }

    public static void setAnywhereForTest(boolean anywhere) {
        anywhereForTest = anywhere;
    }

    public static boolean isVein(BlockState state) {
        return state.getBlock() instanceof MyceliumVeinBlock;
    }

    public static boolean isNode(BlockState state) {
        return state.getBlock() instanceof MyceliumNodeBlock;
    }

    // ---- ticks aleatorios ----

    public static void veinTick(ServerLevel level, BlockPos pos, RandomSource random) {
        if (MyceliumRules.scaredBy(level.getBrightness(LightLayer.BLOCK, pos))) {
            removeVein(level, pos);
            return;
        }
        if (random.nextDouble() < ORPHAN_CHECK && killIfOrphan(level, pos) > 0) {
            return;
        }
        tryGrow(level, pos, random);
    }

    public static void nodeTick(ServerLevel level, BlockPos pos, RandomSource random) {
        tryGrow(level, pos, random);
        tryGrow(level, pos, random);
    }

    private static void tryGrow(ServerLevel level, BlockPos from, RandomSource random) {
        if (!StormrootConfig.caveChangesActive()) {
            return;
        }
        SeepageCell cell = SeepageSavedData.get(level.getServer()).field().get(from.getX() >> 4, from.getZ() >> 4);
        double deep = cell == null ? 0 : cell.deep();
        boolean foggy = cell != null && cell.foggy();
        if (random.nextDouble() < MyceliumRules.growthChance(deep, foggy)) {
            spread(level, from, random);
        }
    }

    /** Un paso de crecimiento desde {@code from}: un hilo (o, a veces, un nodo) en un hueco de al lado. */
    public static boolean spread(ServerLevel level, BlockPos from, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            BlockPos to = from.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
            if (!to.equals(from) && growInto(level, from, to, random.nextDouble() < MyceliumRules.NODE_CHANCE)) {
                return true;
            }
        }
        return false;
    }

    /** Intenta poner un hilo en {@code to} (o un nodo en el suelo de debajo, si {@code node}). */
    public static boolean growInto(ServerLevel level, BlockPos from, BlockPos to, boolean node) {
        if (!level.isLoaded(to) || !level.getBlockState(to).isAir()) {
            return false;
        }
        if (MyceliumRules.scaredBy(level.getBrightness(LightLayer.BLOCK, to)) || !habitable(level, to)) {
            return false;
        }
        BlockState vein = veinStateAt(level, to);
        if (vein == null) {
            return false;
        }
        BlockPos floor = to.below();
        boolean makeNode = node && isNatural(level.getBlockState(floor)) && !nodeNear(level, floor, MyceliumRules.NODE_SPACING);
        if (!networks(level).tryGrow(chunkKey(from), chunkKey(to))) {
            return false;
        }
        if (makeNode) {
            level.setBlock(floor, ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(to, vein, Block.UPDATE_ALL);
        }
        SeepageSavedData.get(level.getServer()).changed();
        return true;
    }

    /** Hilo con una cara en cada bloque sólido de alrededor, o {@code null} si no hay donde agarrarse. */
    public static BlockState veinStateAt(ServerLevel level, BlockPos pos) {
        BlockState state = ModBlocks.MYCELIUM_VEIN.defaultBlockState();
        boolean any = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            BlockState other = level.getBlockState(neighbor);
            if (other.isFaceSturdy(level, neighbor, direction.getOpposite()) && !isNode(other)) {
                state = state.setValue(MultifaceBlock.getFaceProperty(direction), true);
                any = true;
            }
        }
        return any ? state : null;
    }

    /** Bajo un bioma de la red y bajo tierra (o en cualquier sitio, durante el self-test). */
    public static boolean habitable(ServerLevel level, BlockPos pos) {
        if (anywhereForTest) {
            return true;
        }
        if (!HostBiomes.isHostColumn(level, pos.getX(), pos.getZ()) || level.canSeeSky(pos)) {
            return false;
        }
        int surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY();
        return pos.getY() <= surface - MIN_DEPTH;
    }

    private static boolean isNatural(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT);
    }

    private static boolean nodeNear(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (isNode(level.getBlockState(p))) {
                return true;
            }
        }
        return false;
    }

    // ---- luz y nodos rotos ----

    public static void removeVein(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean water = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
        level.setBlock(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * Si el hilo de {@code pos} ya no está unido (hilo a hilo) a ningún nodo, muere con todos los hilos de su trozo.
     * Devuelve cuántos murieron (0 si sigue vivo, o si la red es tan grande que no se ha podido recorrer entera).
     */
    public static int killIfOrphan(ServerLevel level, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> veins = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos.immutable());
        queue.add(pos.immutable());
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (isNode(state)) {
                return 0;
            }
            if (!isVein(state)) {
                continue;
            }
            veins.add(at);
            if (seen.size() > MyceliumRules.CONNECTION_SEARCH) {
                return 0;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (seen.add(next) && level.isLoaded(next)) {
                            BlockState s = level.getBlockState(next);
                            if (isVein(s) || isNode(s)) {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        }
        for (BlockPos vein : veins) {
            removeVein(level, vein);
        }
        return veins.size();
    }

    // ---- nacimiento (cada 2 s, cerca de los jugadores) ----

    public static void tick(ServerLevel level) {
        if (++ticks < BIRTH_INTERVAL || !StormrootConfig.caveChangesActive()) {
            return;
        }
        ticks = 0;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!HostBiomes.isHostColumn(level, player.getBlockX(), player.getBlockZ())) {
                continue;
            }
            maybeSpawnSporeling(level, player, random);
            for (int i = 0; i < BIRTH_TRIES; i++) {
                if (random.nextDouble() >= BIRTH_CHANCE) {
                    continue;
                }
                BlockPos pos = player.blockPosition().offset(random.nextInt(BIRTH_RANGE * 2 + 1) - BIRTH_RANGE,
                        random.nextInt(BIRTH_RANGE + 1) - BIRTH_RANGE, random.nextInt(BIRTH_RANGE * 2 + 1) - BIRTH_RANGE);
                tryBirth(level, pos);
            }
        }
    }

    // ---- habitantes ----

    /** Esporines como mucho a 32 bloques de un jugador. */
    public static final int MAX_SPORELINGS = 6;
    private static final double SPORELING_CHANCE = 0.15;

    /** De vez en cuando aparece un esporín sobre un hilo a oscuras (más con niebla; ninguno con sequía). */
    private static void maybeSpawnSporeling(ServerLevel level, ServerPlayer player, RandomSource random) {
        AABB around = player.getBoundingBox().inflate(32);
        if (level.getEntities(ModEntities.SPORELING, around, e -> true).size() >= MAX_SPORELINGS) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            BlockPos pos = player.blockPosition().offset(random.nextInt(49) - 24, random.nextInt(17) - 8, random.nextInt(49) - 24);
            if (!level.isLoaded(pos) || !isVein(level.getBlockState(pos))) {
                continue;
            }
            SeepageCell cell = SeepageSavedData.get(level.getServer()).field().get(pos.getX() >> 4, pos.getZ() >> 4);
            double deep = cell == null ? 0 : cell.deep();
            double chance = deep < MyceliumRules.DRY_AT ? 0 : SPORELING_CHANCE * (cell != null && cell.foggy() ? 2 : 1);
            if (random.nextDouble() < chance && spawnSporeling(level, pos) != null) {
                return;
            }
        }
    }

    /** Hace aparecer un esporín en {@code pos} si cabe y está a oscuras. */
    public static Entity spawnSporeling(ServerLevel level, BlockPos pos) {
        if (MyceliumRules.scaredBy(level.getBrightness(LightLayer.BLOCK, pos))
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return null;
        }
        //? if >=1.21.2 {
        return ModEntities.SPORELING.spawn(level, pos, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
        //?} else {
        /*return ModEntities.SPORELING.spawn(level, pos, net.minecraft.world.entity.MobSpawnType.NATURAL);
        *///?}
    }

    /** Nace una red con su primer nodo en el suelo de {@code air}, si se dan las condiciones. */
    public static boolean tryBirth(ServerLevel level, BlockPos air) {
        if (!level.isLoaded(air) || !level.getBlockState(air).isAir() || !habitable(level, air)
                || MyceliumRules.scaredBy(level.getBrightness(LightLayer.BLOCK, air))) {
            return false;
        }
        BlockPos floor = air.below();
        if (!isNatural(level.getBlockState(floor))) {
            return false;
        }
        SeepageSavedData data = SeepageSavedData.get(level.getServer());
        SeepageCell cell = data.field().get(air.getX() >> 4, air.getZ() >> 4);
        if (!anywhereForTest && (cell == null || !MyceliumRules.canBeBorn(cell.deep()))) {
            return false;
        }
        int cx = air.getX() >> 4;
        int cz = air.getZ() >> 4;
        MyceliumNetworks networks = data.mycelium();
        for (int dx = -BIRTH_SPACING_CHUNKS; dx <= BIRTH_SPACING_CHUNKS; dx++) {
            for (int dz = -BIRTH_SPACING_CHUNKS; dz <= BIRTH_SPACING_CHUNKS; dz++) {
                if (networks.networkAt(SeepageField.key(cx + dx, cz + dz)) != 0) {
                    return false;
                }
            }
        }
        networks.create(SeepageField.key(cx, cz));
        level.setBlock(floor, ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_ALL);
        data.changed();
        return true;
    }

    public static MyceliumNetworks networks(ServerLevel level) {
        return SeepageSavedData.get(level.getServer()).mycelium();
    }

    public static long chunkKey(BlockPos pos) {
        return SeepageField.key(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
