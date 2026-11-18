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
                        SeepageCell cell = field.getOrCreate(cx, cz);
                        cell.update(rain, fog, seconds, rates);
                        cell.setMonsoon(intensityAt(simulation, FrontType.MONSOON, (cx << 4) + 8, (cz << 4) + 8));
                        if (rain < 0.05) {
                            cell.applyHeat(intensityAt(simulation, FrontType.HEAT_WAVE, (cx << 4) + 8, (cz << 4) + 8), seconds, rates);
                        }
                        updateFrost(level, simulation, cell, cx, cz, seconds);
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
            } else if (front.type() == FrontType.MONSOON) {
                // Monzón: llena el subsuelo tres veces más rápido.
                best = Math.max(best, front.intensityAt(x, z) * MONSOON_WETTING);
            }
        }
        return best;
    }

    /** Cuánto más moja el monzón que la lluvia. */
    public static final double MONSOON_WETTING = 3.0;

    /** Cuevas de hielo: a partir de esta helada, nieve y hielo en el suelo. */
    public static final double SNOW_AT = 0.2;
    /** Agua natural congelada. */
    public static final double FREEZE_WATER_AT = 0.35;
    /** Carámbanos en el techo (y frío que congela a los jugadores, {@link ColdCaves}). */
    public static final double ICICLE_AT = 0.45;
    /** Cristales de hielo. */
    public static final double CRYSTAL_AT = 0.55;
    /** Los carámbanos crecen (hasta 3). */
    public static final double ICICLE_GROW_AT = 0.65;
    /** Helada mínima para que una tormenta encima la derrita de golpe (deshielo masivo). */
    public static final double MASSIVE_THAW_FROST = 0.6;
    /** Intensidad de tormenta (u ola de calor) que desata el deshielo masivo. */
    public static final double MASSIVE_THAW_STORM = 0.5;

    /**
     * Helada del chunk: sube con la ventisca encima (solo en biomas fríos: ahí se hielan todas las cuevas); sin ella
     * baja muy despacio; una tormenta fuerte sobre una cueva muy helada la derrite de golpe (deshielo masivo).
     * Más adelante, la ola de calor también.
     */
    private static void updateFrost(ServerLevel level, FrontSimulation simulation, SeepageCell cell, int cx, int cz, double seconds) {
        double x = (cx << 4) + 8;
        double z = (cz << 4) + 8;
        double blizzard = intensityAt(simulation, FrontType.BLIZZARD, x, z);
        if (blizzard > 0.05) {
            io.github.hcuadrado428.stormroot.weather.ClimateZone zone =
                    io.github.hcuadrado428.stormroot.weather.BiomeClimate.zoneAt(level, x, z);
            if (zone != io.github.hcuadrado428.stormroot.weather.ClimateZone.COLD
                    && zone != io.github.hcuadrado428.stormroot.weather.ClimateZone.MOUNTAIN) {
                blizzard = 0;
            }
        }
        // Deshielo masivo: una tormenta fuerte o una ola de calor sobre una cueva muy helada.
        double warm = Math.max(intensityAt(simulation, FrontType.THUNDERSTORM, x, z),
                intensityAt(simulation, FrontType.HEAT_WAVE, x, z));
        boolean thaw = warm >= MASSIVE_THAW_STORM && (cell.frost() >= MASSIVE_THAW_FROST || cell.thawing());
        cell.updateFrost(blizzard, thaw, seconds);
    }

    /** Intensidad (0–1) del frente más fuerte de ese tipo en un punto. */
    public static double intensityAt(FrontSimulation simulation, FrontType type, double x, double z) {
        double best = 0;
        for (WeatherFront front : simulation.fronts()) {
            if (front.type() == type) {
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

        // Cuevas de hielo: deshacer lo que ya no toca (deprisa en un deshielo masivo) y, con helada, helar.
        double frost = cell.frost();
        double thawChance = cell.thawing() ? Math.min(1, RECEDE_CHANCE * 6) : RECEDE_CHANCE;
        if (frost < CRYSTAL_AT - 0.1 && random.nextDouble() < thawChance) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.ICE_CRYSTAL));
        }
        if (frost < ICICLE_AT - 0.1 && random.nextDouble() < thawChance) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.ICICLE));
        }
        if (frost < SNOW_AT - 0.1 && random.nextDouble() < thawChance) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.SNOW_FLOOR, SeepageCell.Kind.ICE_FLOOR));
        }
        if (frost < FREEZE_WATER_AT - 0.1 && random.nextDouble() < thawChance) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.FROZEN));
        }
        if (frost >= SNOW_AT) {
            // Cueva helada: no se moja (no hay charcos ni barro nuevos), solo se hiela.
            if (cell.changes().size() < maxChanges) {
                freeze(level, cx, cz, cell, random, frost);
            }
            return;
        }

        // Monzón: cascadas del techo y orillas inundadas; al irse, se retiran.
        double monsoon = cell.monsoon();
        if ((monsoon < 0.1 || deep < CASCADE_AT - 0.2) && random.nextDouble() < RECEDE_CHANCE) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.CASCADE));
        }
        if (monsoon < 0.1 && random.nextDouble() < RECEDE_CHANCE) {
            undo(level, cell, cell.lastIndexOf(SeepageCell.Kind.FLOOD));
        }
        if (monsoon >= 0.3 && cell.changes().size() < maxChanges) {
            if (random.nextInt(3) == 0) {
                floodBank(level, cx, cz, cell, random);
            }
            if (deep >= CASCADE_AT && cell.count(SeepageCell.Kind.CASCADE) < MAX_CASCADES && random.nextInt(4) == 0) {
                cascade(level, cx, cz, cell, random);
            }
        }

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

    /** Un efecto de cueva helada por llamada: suelo de nieve o hielo, agua congelada, carámbanos o cristales. */
    private static void freeze(ServerLevel level, int cx, int cz, SeepageCell cell, RandomSource random, double frost) {
        // Primero hacer crecer carámbanos que ya hay.
        if (frost >= ICICLE_GROW_AT && cell.count(SeepageCell.Kind.ICICLE) > 0 && random.nextInt(3) == 0 && growIcicle(level, cell, random)) {
            return;
        }
        for (int attempt = 0; attempt < ATTEMPTS_PER_CHUNK; attempt++) {
            if (frost >= FREEZE_WATER_AT && random.nextInt(4) == 0 && freezeWater(level, cx, cz, cell, random)) {
                return;
            }
            BlockPos air = randomCaveFloor(level, cx, cz, random);
            if (air == null) {
                continue;
            }
            BlockPos floor = air.below();
            String material = CaveMaterials.idOf(level.getBlockState(floor));
            if (material == null || !CaveMaterials.PUDDLE_FLOOR.contains(material) || !isWall(level, floor)) {
                continue;
            }
            int roll = random.nextInt(10);
            if (frost >= ICICLE_AT && roll < 3) {
                BlockPos hang = ceilingAbove(level, air);
                if (hang != null && place(level, cell, hang, io.github.hcuadrado428.stormroot.registry.ModBlocks.ICICLE.defaultBlockState(),
                        SeepageCell.Kind.ICICLE, "")) {
                    return;
                }
            } else if (frost >= CRYSTAL_AT && roll == 3) {
                if (place(level, cell, air, io.github.hcuadrado428.stormroot.registry.ModBlocks.ICE_CRYSTAL.defaultBlockState(),
                        SeepageCell.Kind.ICE_CRYSTAL, "")) {
                    return;
                }
            } else if (roll < 7) {
                // Nieve y hielo mezclados en el suelo.
                int layers = 1 + random.nextInt(2);
                if (place(level, cell, air, Blocks.SNOW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers), SeepageCell.Kind.SNOW_FLOOR, "")) {
                    return;
                }
            } else if (place(level, cell, floor, Blocks.PACKED_ICE.defaultBlockState(), SeepageCell.Kind.ICE_FLOOR, material)) {
                return;
            }
        }
    }

    /** Humedad del subsuelo a partir de la cual el monzón hace cascadas en las cuevas. */
    public static final double CASCADE_AT = 0.9;
    /** Cascadas por chunk como mucho. */
    public static final int MAX_CASCADES = 2;
    /** Hasta cuántos bloques del agua natural crece una orilla. */
    public static final int MAX_FLOOD_REACH = 2;

    /**
     * Orilla inundada: un bloque de suelo natural (hierba, tierra, arena, grava, arcilla, barro) al nivel del agua, junto
     * a agua quieta, a cielo abierto y sin nada encima, pasa a ser agua. Como mucho {@link #MAX_FLOOD_REACH} bloques desde
     * el agua natural. Se restaura al irse el monzón.
     */
    private static void floodBank(ServerLevel level, int cx, int cz, SeepageCell cell, RandomSource random) {
        int x = (cx << 4) + random.nextInt(16);
        int z = (cz << 4) + random.nextInt(16);
        BlockPos above = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        BlockPos water = above.below();
        if (!isStillWater(level, water)) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (floodNext(level, cell, water.relative(side))) {
                return;
            }
        }
    }

    /** Inunda {@code bank} si se puede (ver {@link #floodBank}). Público para el self-test. */
    public static boolean floodNext(ServerLevel level, SeepageCell cell, BlockPos bank) {
        BlockState state = level.getBlockState(bank);
        String material = CaveMaterials.idOf(state);
        if (material == null || !FLOOD_GROUND.contains(material) || !level.getBlockState(bank.above()).isAir()
                || !level.canSeeSky(bank.above()) || floodDistance(level, cell, bank) > MAX_FLOOD_REACH) {
            return false;
        }
        return place(level, cell, bank, Blocks.WATER.defaultBlockState(), SeepageCell.Kind.FLOOD, material);
    }

    private static final java.util.Set<String> FLOOD_GROUND =
            java.util.Set.of("grass_block", "dirt", "coarse_dirt", "sand", "gravel", "clay", "mud");

    /** Distancia (1, 2...) desde el agua natural a través de orillas ya inundadas; grande si no toca agua. */
    private static int floodDistance(ServerLevel level, SeepageCell cell, BlockPos bank) {
        int best = Integer.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = bank.relative(side);
            if (!isStillWater(level, next)) {
                continue;
            }
            if (!hasChange(cell, next)) {
                return 1;
            }
            for (Direction side2 : Direction.Plane.HORIZONTAL) {
                BlockPos second = next.relative(side2);
                if (!second.equals(bank) && isStillWater(level, second) && !hasChange(cell, second)) {
                    best = Math.min(best, 2);
                }
            }
        }
        return best;
    }

    /** Cascada temporal: agua que cae del techo de roca de una cueva (al quitarla, el agua que corre se va sola). */
    private static void cascade(ServerLevel level, int cx, int cz, SeepageCell cell, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS_PER_CHUNK; attempt++) {
            BlockPos air = randomCaveFloor(level, cx, cz, random);
            if (air == null) {
                continue;
            }
            BlockPos spout = ceilingAbove(level, air);
            if (spout != null && spout.getY() >= air.getY() + 2
                    && place(level, cell, spout, Blocks.WATER.defaultBlockState(), SeepageCell.Kind.CASCADE, "")) {
                return;
            }
        }
    }

    /** Solo para el self-test: una cascada en el chunk. */
    public static void cascadeForTest(ServerLevel level, int cx, int cz, SeepageCell cell) {
        for (int i = 0; i < 20 && cell.count(SeepageCell.Kind.CASCADE) == 0; i++) {
            cascade(level, cx, cz, cell, level.getRandom());
        }
    }

    /** El aire justo bajo el techo encima de {@code air} (techo de roca natural), o {@code null}. */
    private static BlockPos ceilingAbove(ServerLevel level, BlockPos air) {
        BlockPos pos = air;
        for (int i = 0; i < 12; i++) {
            BlockPos up = pos.above();
            BlockState state = level.getBlockState(up);
            if (!state.isAir()) {
                String material = CaveMaterials.idOf(state);
                return material != null && CaveMaterials.ROCK.contains(material) && isWall(level, up)
                        && pos.getY() > air.getY() + 1 ? pos : null;
            }
            pos = up;
        }
        return null;
    }

    /** Alarga un carámbano (hasta 3) por debajo de su punta. */
    private static boolean growIcicle(ServerLevel level, SeepageCell cell, RandomSource random) {
        List<SeepageCell.Change> icicles = new ArrayList<>();
        for (SeepageCell.Change change : cell.changes()) {
            if (change.kind() == SeepageCell.Kind.ICICLE) {
                icicles.add(change);
            }
        }
        BlockPos tip = BlockPos.of(icicles.get(random.nextInt(icicles.size())).pos());
        BlockState state = level.getBlockState(tip);
        if (!level.isLoaded(tip) || !(state.getBlock() instanceof io.github.hcuadrado428.stormroot.block.IcicleBlock)
                || !state.getValue(io.github.hcuadrado428.stormroot.block.IcicleBlock.TIP)) {
            return false;
        }
        BlockPos below = tip.below();
        if (io.github.hcuadrado428.stormroot.block.IcicleBlock.lengthAt(level, tip) >= io.github.hcuadrado428.stormroot.block.IcicleBlock.MAX_LENGTH
                || !level.getBlockState(below).isAir() || !level.getBlockState(below.below()).isAir()) {
            return false;
        }
        // El de arriba deja de ser punta; el nuevo es la punta. El cambio de arriba sigue apuntado (es el mismo bloque).
        level.setBlock(tip, state.setValue(io.github.hcuadrado428.stormroot.block.IcicleBlock.TIP, false), Block.UPDATE_ALL);
        return place(level, cell, below, state.setValue(io.github.hcuadrado428.stormroot.block.IcicleBlock.TIP, true),
                SeepageCell.Kind.ICICLE, "");
    }

    /** Congela un bloque de agua natural quieta de una cueva del chunk (no el agua que puso Stormroot). */
    private static boolean freezeWater(ServerLevel level, int cx, int cz, SeepageCell cell, RandomSource random) {
        BlockPos air = randomCaveFloor(level, cx, cz, random);
        if (air == null) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos water = air.below().relative(side);
            if (isStillWater(level, water) && level.getBlockState(water.above()).isAir() && !level.canSeeSky(water.above())
                    && !hasChange(cell, water)) {
                return place(level, cell, water, Blocks.ICE.defaultBlockState(), SeepageCell.Kind.FROZEN, "water");
            }
        }
        return false;
    }

    private static boolean hasChange(SeepageCell cell, BlockPos pos) {
        long key = pos.asLong();
        for (SeepageCell.Change change : cell.changes()) {
            if (change.pos() == key) {
                return true;
            }
        }
        return false;
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
            // Un charco helado por la helada sigue siendo "su" charco: al secarse vuelve al material de antes.
            case PUDDLE, POOL -> (state.is(Blocks.WATER) && level.getFluidState(pos).isSource())
                    || state.is(io.github.hcuadrado428.stormroot.registry.ModBlocks.FROST_ICE);
            case MUD -> state.is(Blocks.MUD);
            case MOSS -> state.is(Blocks.MOSS_BLOCK);
            case CRACKED -> state.is(Blocks.COARSE_DIRT);
            case SNOW_FLOOR -> state.is(Blocks.SNOW);
            case ICE_FLOOR -> state.is(Blocks.PACKED_ICE);
            case FROZEN -> state.is(Blocks.ICE);
            case ICICLE -> state.getBlock() instanceof io.github.hcuadrado428.stormroot.block.IcicleBlock;
            case ICE_CRYSTAL -> state.getBlock() instanceof io.github.hcuadrado428.stormroot.block.IceCrystalBlock;
            case FLOOD, CASCADE -> state.is(Blocks.WATER);
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
