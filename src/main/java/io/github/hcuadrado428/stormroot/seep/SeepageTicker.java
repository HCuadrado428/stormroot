package io.github.hcuadrado428.stormroot.seep;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Filtración: conecta {@link SeepageField} (Java puro) con el mundo. Lo llama {@code FrontTicker} en cada paso.
 *
 * <ol>
 *   <li>Actualiza la humedad de los chunks cercanos a los jugadores según la lluvia de los frentes.</li>
 *   <li>En los chunks más cercanos, si el subsuelo está empapado, crea charcos y barro en el suelo de las cuevas;
 *       si se ha secado, los deshace (solo lo que creó Stormroot y solo si sigue igual).</li>
 * </ol>
 * Los chunks lejos de los jugadores no cambian: su registro se queda como estaba.
 */
public final class SeepageTicker {
    /** Radio (en chunks) alrededor de cada jugador en el que se actualiza la humedad. */
    public static final int UPDATE_RADIUS = 6;
    /** Radio (en chunks) en el que las cuevas cambian. */
    public static final int EFFECT_RADIUS = 3;
    /** Subsuelo a partir del cual aparecen charcos y barro. */
    public static final double FLOOD_THRESHOLD = 0.55;
    /** Subsuelo por debajo del cual se deshacen. */
    public static final double RECEDE_THRESHOLD = 0.2;
    /** Máximo de bloques cambiados por chunk. */
    public static final int MAX_CHANGES_PER_CHUNK = 12;
    private static final int ATTEMPTS_PER_CHUNK = 6;
    /** Los charcos solo aparecen a esta profundidad bajo la superficie, como mínimo. */
    private static final int MIN_DEPTH = 10;

    private SeepageTicker() {
    }

    public static void step(MinecraftServer server, FrontSimulation simulation, double seconds) {
        if (!StormrootConfig.seepageEnabled.getAsBoolean()) {
            return;
        }
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        SeepageSavedData data = SeepageSavedData.get(server);
        SeepageField field = data.field();
        SeepageRates rates = SeepageRates.DEFAULT.scaled(StormrootConfig.seepageSpeed.getAsDouble());

        Set<Long> updated = new HashSet<>();
        Set<Long> affected = new HashSet<>();
        for (ServerPlayer player : players) {
            int pcx = player.getBlockX() >> 4;
            int pcz = player.getBlockZ() >> 4;
            for (int dx = -UPDATE_RADIUS; dx <= UPDATE_RADIUS; dx++) {
                for (int dz = -UPDATE_RADIUS; dz <= UPDATE_RADIUS; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (updated.add(SeepageField.key(cx, cz))) {
                        double rain = rainAt(simulation, (cx << 4) + 8, (cz << 4) + 8);
                        field.getOrCreate(cx, cz).update(rain, seconds, rates);
                    }
                    if (Math.abs(dx) <= EFFECT_RADIUS && Math.abs(dz) <= EFFECT_RADIUS) {
                        affected.add(SeepageField.key(cx, cz));
                    }
                }
            }
        }

        if (StormrootConfig.seepageWorldChanges.getAsBoolean()) {
            for (long key : affected) {
                int cx = SeepageField.keyX(key);
                int cz = SeepageField.keyZ(key);
                SeepageCell cell = field.get(cx, cz);
                if (cell != null && level.hasChunk(cx, cz)) {
                    applyEffects(level, cx, cz, cell);
                }
            }
        }
        field.prune();
        data.changed();
    }

    /** Humedad del subsuelo en el chunk de una posición (0 si no hay registro). */
    public static double deepAt(MinecraftServer server, int blockX, int blockZ) {
        return SeepageSavedData.get(server).field().deep(blockX >> 4, blockZ >> 4);
    }

    /** Intensidad de lluvia (0–1) de los frentes en un punto; las tormentas cuentan como lluvia. */
    public static double rainAt(FrontSimulation simulation, double x, double z) {
        double best = 0;
        for (WeatherFront front : simulation.fronts()) {
            if (front.type() == FrontType.RAIN || front.type() == FrontType.THUNDERSTORM) {
                best = Math.max(best, front.intensityAt(x, z));
            }
        }
        return best;
    }

    private static void applyEffects(ServerLevel level, int cx, int cz, SeepageCell cell) {
        if (cell.deep() >= FLOOD_THRESHOLD && cell.changes().size() < MAX_CHANGES_PER_CHUNK) {
            tryFlood(level, cx, cz, cell);
        } else if (cell.deep() <= RECEDE_THRESHOLD && !cell.changes().isEmpty()) {
            recedeOne(level, cell);
        }
    }

    private static void tryFlood(ServerLevel level, int cx, int cz, SeepageCell cell) {
        RandomSource random = level.getRandom();
        //? if >=1.21.2 {
        int minY = level.getMinY();
        //?} else {
        /*int minY = level.getMinBuildHeight();
        *///?}
        for (int attempt = 0; attempt < ATTEMPTS_PER_CHUNK; attempt++) {
            int x = (cx << 4) + random.nextInt(16);
            int z = (cz << 4) + random.nextInt(16);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - MIN_DEPTH;
            if (top <= minY + 2) {
                continue;
            }
            BlockPos start = new BlockPos(x, minY + 1 + random.nextInt(top - minY - 1), z);
            BlockPos floorAir = findCaveFloor(level, start);
            if (floorAir == null) {
                continue;
            }
            BlockPos below = floorAir.below();
            BlockState ground = level.getBlockState(below);
            if (ground.is(Blocks.DIRT)) {
                level.setBlock(below, Blocks.MUD.defaultBlockState(), Block.UPDATE_ALL);
                cell.changes().add(new SeepageCell.Change(below.asLong(), SeepageCell.Kind.MUD));
                return;
            }
            if (ground.is(BlockTags.BASE_STONE_OVERWORLD)) {
                level.setBlock(floorAir, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                cell.changes().add(new SeepageCell.Change(floorAir.asLong(), SeepageCell.Kind.PUDDLE));
                return;
            }
        }
    }

    /** Desde un punto de aire de cueva, baja hasta el suelo. {@code null} si no es una cueva válida. */
    private static BlockPos findCaveFloor(ServerLevel level, BlockPos start) {
        if (!level.getBlockState(start).isAir() || level.canSeeSky(start)) {
            return null;
        }
        BlockPos pos = start;
        for (int i = 0; i < 16; i++) {
            BlockPos below = pos.below();
            BlockState state = level.getBlockState(below);
            if (!state.isAir()) {
                return state.getFluidState().isEmpty() ? pos : null;
            }
            pos = below;
        }
        return null;
    }

    private static void recedeOne(ServerLevel level, SeepageCell cell) {
        SeepageCell.Change change = cell.changes().get(cell.changes().size() - 1);
        BlockPos pos = BlockPos.of(change.pos());
        if (!level.isLoaded(pos)) {
            return;
        }
        cell.changes().remove(cell.changes().size() - 1);
        BlockState state = level.getBlockState(pos);
        if (change.kind() == SeepageCell.Kind.PUDDLE) {
            if (state.is(Blocks.WATER) && level.getFluidState(pos).isSource()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (change.kind() == SeepageCell.Kind.MUD) {
            if (state.is(Blocks.MUD)) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
