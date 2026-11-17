package io.github.hcuadrado428.stormroot.seep;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Filtración: conecta {@link SeepageField} (Java puro) con el mundo. Lo llama {@code FrontTicker} en cada paso (5 s).
 *
 * <p>Según la humedad del subsuelo ({@code deep}) del chunk, en el suelo de las cuevas:
 * <ul>
 *   <li>≥ {@link #MUD_AT}: la tierra se vuelve barro.</li>
 *   <li>≥ {@link #PUDDLE_AT}: aparecen charcos (un bloque del suelo pasa a ser agua, encerrado) y los que hay crecen.</li>
 *   <li>≥ {@link #POOL_AT}: los hoyos naturales cerrados se llenan de agua (pozas).</li>
 *   <li>≥ {@link #MOSS_AT}: a veces sale musgo en la roca.</li>
 *   <li>≤ {@link #RECEDE_AT}: el agua se retira poco a poco (primero charcos, luego pozas, luego barro).</li>
 *   <li>≤ {@link #DROUGHT_AT}: la tierra y el barro se agrietan; el musgo se seca. Se deshace al volver la humedad.</li>
 * </ul>
 * Solo se tocan bloques de {@link CaveMaterials} (o aire, en las pozas), siempre se apunta qué había y solo se
 * deshace si el bloque sigue tal cual lo dejó Stormroot. El agua nunca queda abierta: no se derrama por la cueva.
 */
public final class SeepageTicker {
    /** Radio (en chunks) alrededor de cada jugador en el que se actualiza la humedad. */
    public static final int UPDATE_RADIUS = 6;
    /** Radio (en chunks) en el que las cuevas cambian. */
    public static final int EFFECT_RADIUS = 3;

    public static final double MUD_AT = 0.4;
    public static final double PUDDLE_AT = 0.55;
    public static final double MOSS_AT = 0.75;
    /** Musgo bajo la niebla (que no pasa de {@link SeepageCell#FOG_CAP}). */
    public static final double FOG_MOSS_AT = 0.45;
    public static final double POOL_AT = 0.8;
    public static final double RECEDE_AT = 0.1;
    public static final double DROUGHT_AT = -0.55;

    /** Probabilidad por paso de deshacer un cambio cuando toca secarse: hace que el secado dure. */
    private static final double RECEDE_CHANCE = 0.35;
    private static final int ATTEMPTS_PER_CHUNK = 8;
    /** Los cambios solo ocurren a esta profundidad bajo la superficie, como mínimo. */
    private static final int MIN_DEPTH = 10;
    /** Tamaño máximo de una poza (bloques de agua de una capa). */
    private static final int MAX_POOL = 24;
    /** Los charcos pueden ocupar como mucho esta parte de los cambios del chunk (deja sitio a barro, musgo y pozas). */
    private static final double MAX_PUDDLE_SHARE = 0.6;

    private SeepageTicker() {
    }

    public static void step(MinecraftServer server, FrontSimulation simulation, double seconds) {
        if (!StormrootConfig.seepageActive()) {
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
                        double fog = fogAt(simulation, (cx << 4) + 8, (cz << 4) + 8);
                        field.getOrCreate(cx, cz).update(rain, fog, seconds, rates);
                    }
                    if (Math.abs(dx) <= EFFECT_RADIUS && Math.abs(dz) <= EFFECT_RADIUS) {
                        affected.add(SeepageField.key(cx, cz));
                    }
                }
            }
        }

        if (StormrootConfig.caveChangesActive()) {
            int maxChanges = StormrootConfig.seepageMaxChangesPerChunk.getAsInt();
            for (long key : affected) {
                int cx = SeepageField.keyX(key);
                int cz = SeepageField.keyZ(key);
                SeepageCell cell = field.get(cx, cz);
                if (cell != null && level.hasChunk(cx, cz)) {
                    applyEffects(level, cx, cz, cell, maxChanges);
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

    /** Intensidad de niebla (0–1) de los frentes en un punto. */
    public static double fogAt(FrontSimulation simulation, double x, double z) {
        double best = 0;
        for (WeatherFront front : simulation.fronts()) {
            if (front.type() == FrontType.FOG) {
                best = Math.max(best, front.intensityAt(x, z));
            }
        }
        return best;
    }

    /** Solo para {@code selftest/SelfTest}: aplica los efectos a una celda concreta, sin esperar a los jugadores. */
    public static void applyEffectsForTest(ServerLevel level, int cx, int cz, SeepageCell cell, int maxChanges) {
        applyEffects(level, cx, cz, cell, maxChanges);
    }

    /** Solo para {@code selftest/SelfTest}: intenta rellenar la poza que contiene {@code start}. */
    public static boolean fillPoolForTest(ServerLevel level, SeepageCell cell, BlockPos start, int maxChanges) {
        return fillPool(level, cell, start, maxChanges);
    }

    // ------------------------------------------------------------------ efectos

    private static void applyEffects(ServerLevel level, int cx, int cz, SeepageCell cell, int maxChanges) {
        RandomSource random = level.getRandom();
        double deep = cell.deep();

        // Secado y sequía primero: deshacen cambios.
        if (deep <= RECEDE_AT && random.nextDouble() < RECEDE_CHANCE) {
            // Primero los charcos (vuelven a ser piedra: nunca abren hueco) y luego las pozas; si se vaciara antes una poza
            // con un charco al lado, el charco se derramaría hacia ella.
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.PUDDLE, SeepageCell.Kind.POOL, SeepageCell.Kind.MUD));
        }
        if (deep <= DROUGHT_AT && random.nextDouble() < RECEDE_CHANCE) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.MOSS));
        }
        if (deep > 0 && random.nextDouble() < RECEDE_CHANCE) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.CRACKED));
        }
        if (cell.changes().size() >= maxChanges) {
            return;
        }

        boolean morePuddles = cell.count(SeepageCell.Kind.PUDDLE) < maxChanges * MAX_PUDDLE_SHARE;

        // Crecer: primero los charcos que ya hay.
        if (deep >= PUDDLE_AT && morePuddles && cell.count(SeepageCell.Kind.PUDDLE) > 0 && random.nextBoolean()
                && growPuddle(level, cell, random)) {
            return;
        }

        for (int attempt = 0; attempt < ATTEMPTS_PER_CHUNK; attempt++) {
            BlockPos air = randomCaveFloor(level, cx, cz, random);
            if (air == null) {
                continue;
            }
            BlockPos floor = air.below();
            String material = CaveMaterials.idOf(level.getBlockState(floor));
            if (material == null) {
                continue;
            }
            if (deep >= POOL_AT && random.nextInt(3) == 0 && fillPool(level, cell, air, maxChanges)) {
                return;
            }
            if (deep >= MUD_AT && CaveMaterials.SOIL.contains(material)
                    && place(level, cell, floor, Blocks.MUD.defaultBlockState(), SeepageCell.Kind.MUD, material)) {
                return;
            }
            // Musgo: con mucha humedad, o con la niebla encima aunque no llegue a haber charcos.
            if ((deep >= MOSS_AT || (cell.foggy() && deep >= FOG_MOSS_AT)) && random.nextInt(6) == 0 && CaveMaterials.ROCK.contains(material)
                    && place(level, cell, floor, Blocks.MOSS_BLOCK.defaultBlockState(), SeepageCell.Kind.MOSS, material)) {
                return;
            }
            if (deep >= PUDDLE_AT && morePuddles && CaveMaterials.PUDDLE_FLOOR.contains(material)
                    && canHoldWater(level, floor) && place(level, cell, floor, Blocks.WATER.defaultBlockState(),
                    SeepageCell.Kind.PUDDLE, material)) {
                return;
            }
            if (deep <= DROUGHT_AT && CaveMaterials.CRACKABLE.contains(material)
                    && place(level, cell, floor, Blocks.COARSE_DIRT.defaultBlockState(), SeepageCell.Kind.CRACKED, material)) {
                return;
            }
        }
    }

    /** Un punto de aire en el suelo de una cueva de ese chunk, o {@code null}. */
    private static BlockPos randomCaveFloor(ServerLevel level, int cx, int cz, RandomSource random) {
        //? if >=1.21.2 {
        int minY = level.getMinY();
        //?} else {
        /*int minY = level.getMinBuildHeight();
        *///?}
        int x = (cx << 4) + random.nextInt(16);
        int z = (cz << 4) + random.nextInt(16);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - MIN_DEPTH;
        if (top <= minY + 2) {
            return null;
        }
        BlockPos pos = new BlockPos(x, minY + 1 + random.nextInt(top - minY - 1), z);
        if (!level.getBlockState(pos).isAir() || level.canSeeSky(pos)) {
            return null;
        }
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

    /**
     * Un bloque del suelo puede pasar a ser agua sin derramarse: debajo y a los cuatro lados hay bloques completos
     * o agua quieta (nunca aire, antorchas, plantas... por donde se escaparía).
     */
    private static boolean canHoldWater(ServerLevel level, BlockPos floor) {
        if (!isWall(level, floor.below())) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = floor.relative(side);
            if (!isWall(level, next) && !isStillWater(level, next)) {
                return false;
            }
        }
        return true;
    }

    /** Hace crecer un charco existente hacia un bloque vecino del mismo nivel. */
    private static boolean growPuddle(ServerLevel level, SeepageCell cell, RandomSource random) {
        List<SeepageCell.Change> puddles = new ArrayList<>();
        for (SeepageCell.Change change : cell.changes()) {
            if (change.kind() == SeepageCell.Kind.PUDDLE) {
                puddles.add(change);
            }
        }
        SeepageCell.Change origin = puddles.get(random.nextInt(puddles.size()));
        BlockPos from = BlockPos.of(origin.pos());
        if (!level.isLoaded(from) || !level.getBlockState(from).is(Blocks.WATER)) {
            return false;
        }
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            sides.add(side);
        }
        java.util.Collections.shuffle(sides, new java.util.Random(random.nextLong()));
        for (Direction side : sides) {
            BlockPos next = from.relative(side);
            if (!level.isLoaded(next) || !level.getBlockState(next.above()).isAir()) {
                continue;
            }
            String material = CaveMaterials.idOf(level.getBlockState(next));
            if (material != null && CaveMaterials.PUDDLE_FLOOR.contains(material) && canHoldWater(level, next)) {
                return place(level, cell, next, Blocks.WATER.defaultBlockState(), SeepageCell.Kind.PUDDLE, material);
            }
        }
        return false;
    }

    /**
     * Rellena con agua un hoyo cerrado del suelo de la cueva que contiene {@code start} (una sola capa,
     * como mucho {@link #MAX_POOL} bloques). Si el hoyo no está cerrado o es demasiado grande, no hace nada.
     */
    private static boolean fillPool(ServerLevel level, SeepageCell cell, BlockPos start, int maxChanges) {
        List<BlockPos> basin = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || level.canSeeSky(pos)
                    || !isWall(level, pos.below())) {
                return false; // se escaparía el agua, o no es una cueva
            }
            basin.add(pos);
            if (basin.size() > MAX_POOL || cell.changes().size() + basin.size() > maxChanges) {
                return false;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                BlockState neighbor = level.getBlockState(next);
                if (neighbor.isAir()) {
                    if (seen.add(next.asLong())) {
                        queue.add(next);
                    }
                } else if (!isWall(level, next)) {
                    return false; // agua, lava, antorchas, plantas...: el agua se escaparía o se mezclaría
                }
            }
        }
        for (BlockPos pos : basin) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            cell.changes().add(new SeepageCell.Change(pos.asLong(), SeepageCell.Kind.POOL, ""));
        }
        return true;
    }

    /**
     * Cambia el bloque y lo apunta. Si ese bloque ya tenía un cambio de Stormroot (p. ej. tierra → barro y ahora
     * barro → charco), el nuevo sustituye al anterior y conserva el material de ANTES de Stormroot, para que al
     * deshacerlo vuelva siempre al bloque original (lo encontró el self-test).
     */
    private static boolean place(ServerLevel level, SeepageCell cell, BlockPos pos, BlockState newState,
                                 SeepageCell.Kind kind, String original) {
        long key = pos.asLong();
        for (int i = 0; i < cell.changes().size(); i++) {
            SeepageCell.Change existing = cell.changes().get(i);
            if (existing.pos() == key) {
                original = existing.original();
                cell.changes().remove(i);
                break;
            }
        }
        level.setBlock(pos, newState, Block.UPDATE_ALL);
        cell.changes().add(new SeepageCell.Change(key, kind, original));
        return true;
    }

    /** Deshace el cambio de ese índice si el bloque sigue tal cual lo dejó Stormroot. */
    private static void undo(ServerLevel level, SeepageCell cell, int index) {
        if (index < 0) {
            return;
        }
        SeepageCell.Change change = cell.changes().get(index);
        BlockPos pos = BlockPos.of(change.pos());
        if (!level.isLoaded(pos)) {
            return;
        }
        cell.changes().remove(index);
        BlockState state = level.getBlockState(pos);
        boolean unchanged = switch (change.kind()) {
            case PUDDLE, POOL -> state.is(Blocks.WATER) && level.getFluidState(pos).isSource();
            case MUD -> state.is(Blocks.MUD);
            case MOSS -> state.is(Blocks.MOSS_BLOCK);
            case CRACKED -> state.is(Blocks.COARSE_DIRT);
        };
        if (unchanged) {
            level.setBlock(pos, CaveMaterials.restore(change.original()), Block.UPDATE_ALL);
        }
    }

    /** Bloque completo sin fluido: el agua no lo atraviesa. */
    private static boolean isWall(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isStillWater(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource();
    }
}
