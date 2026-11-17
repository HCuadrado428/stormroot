package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.block.InstrumentBlock;
import io.github.hcuadrado428.stormroot.network.FrontsSyncPayload;
import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.seep.CaveMaterials;
import io.github.hcuadrado428.stormroot.seep.SeepageCell;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import io.github.hcuadrado428.stormroot.weather.FrontSavedData;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.LightningRoots;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.2 {
import net.minecraft.world.entity.EntitySpawnReason;
//?}

/**
 * Pruebas automáticas dentro del juego. Se activan con {@code -Dstormroot.selfTest=server|client|all}
 * (ver {@code scripts/selftest.sh}); sin esa propiedad no hacen nada.
 *
 * <p>Cada comprobación escribe {@code [selftest] PASS <nombre>} o {@code [selftest] FAIL <nombre>: <motivo>} y al final
 * {@code [selftest] server done: N passed, M failed}. En modo {@code server} el servidor se cierra al terminar
 * ({@code -Dstormroot.selfTest.exit=false} lo deja abierto).
 *
 * <p>Construye su propia zona de pruebas en el chunk (10, 10) del Overworld: un bloque de piedra con una cueva dentro
 * (suelo de piedra y de tierra, y un hoyo cerrado de 2×2), así no depende de la generación del mundo.
 */
public final class SelfTest {
    private static final String MODE = System.getProperty("stormroot.selfTest", "");
    private static final int CX = 10;
    private static final int CZ = 10;
    private static final int BX = CX << 4;
    private static final int BZ = CZ << 4;
    /** Roca de prueba: de Y_BOTTOM a Y_TOP. Cueva: interior x/z +2..+13, y CAVE_FLOOR+1 .. CAVE_FLOOR+5. */
    private static final int Y_BOTTOM = -60;
    private static final int Y_TOP = -21;
    private static final int CAVE_FLOOR = -46;

    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();

    private static int ticks;
    private static int stage;
    private static int waitUntil;
    private static volatile boolean serverDone;
    private static Map<BlockPos, BlockState> snapshot;
    private static SeepageCell cell;

    private SelfTest() {
    }

    public static boolean serverEnabled() {
        return MODE.equals("server") || MODE.equals("all") || MODE.equals("client");
    }

    public static boolean clientEnabled() {
        return MODE.equals("client") || MODE.equals("all");
    }

    public static boolean serverDone() {
        return serverDone;
    }

    public static boolean exitWhenDone() {
        return !"false".equals(System.getProperty("stormroot.selfTest.exit"));
    }

    /** Para el self-test del cliente. */
    public static void report(String name, boolean ok, String why) {
        check(name, ok, why);
    }

    static void check(String name, boolean ok, String why) {
        if (ok) {
            PASSED.incrementAndGet();
            Stormroot.LOGGER.info("[selftest] PASS {}", name);
        } else {
            FAILED.incrementAndGet();
            Stormroot.LOGGER.error("[selftest] FAIL {}: {}", name, why);
        }
    }

    private static void run(String name, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            check(name, false, t.toString());
            Stormroot.LOGGER.error("[selftest] excepción en {}", name, t);
        }
    }

    /** Lo llama {@code FrontTicker} cada tick del servidor. */
    public static void tick(MinecraftServer server) {
        if (!serverEnabled() || serverDone) {
            return;
        }
        ticks++;
        if (ticks < waitUntil) {
            return;
        }
        ServerLevel level = server.overworld();
        switch (stage) {
            case 0 -> {
                if (ticks < 40) {
                    return;
                }
                Stormroot.LOGGER.info("[selftest] servidor: empezando");
                run("registries", SelfTest::checkRegistries);
                run("build test area", () -> buildArea(level));
                run("front codec round trip", () -> checkFrontCodec(server));
                run("sync packet round trip", SelfTest::checkPacket);
                run("commands", () -> checkCommands(server));
                run("world settings", () -> checkSettings(server));
                run("/weather is respected", () -> checkWeatherCommand(server));
                run("stray storm lightning is removed", () -> checkStrayLightning(server));
                run("forecast", SelfTest::checkForecast);
                run("weathervane", () -> checkWeathervane(server, level));
                run("barometer", () -> checkBarometer(server, level));
                run("pool fills a closed hole only", () -> checkPool(level));
                run("seepage floods the cave", () -> floodCave(level));
                run("lightning root starts", () -> LightningRoots.start(level, new BlockPos(BX + 8, Y_TOP, BZ + 8)));
                stage = 1;
                waitUntil = ticks + 160;
            }
            case 1 -> {
                run("water stays contained", () -> checkContained(level));
                run("lightning root grows to the cave", () -> checkRoot(level));
                run("seepage codec round trip", () -> checkSeepageCodec(server));
                run("seepage dries and restores every block", () -> dryCave(level));
                finish(server);
            }
            default -> {
            }
        }
    }

    // --------------------------------------------------------------- comprobaciones

    private static void checkRegistries() {
        boolean ok = ModItems.LIGHTNING_CRYSTAL != null && ModBlocks.WEATHERVANE != null && ModBlocks.BAROMETER != null
                && ModBlocks.LIGHTNING_ROOT != null && ModBlocks.WEATHERVANE_ITEM != null && ModBlocks.BAROMETER_ITEM != null
                && ModBlocks.LIGHTNING_ROOT_ITEM != null;
        check("registries", ok, "algún item o bloque es null");
    }

    private static void buildArea(ServerLevel level) {
        level.setChunkForced(CX, CZ, true);
        level.getChunk(CX, CZ);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(BX, Y_BOTTOM, BZ, BX + 15, Y_TOP + 3, BZ + 15)) {
            level.setBlock(pos, pos.getY() <= Y_TOP ? stone : air, Block.UPDATE_CLIENTS);
        }
        // Cueva: 12×12, 5 de alto, con techo de piedra (muy por debajo de la superficie).
        for (BlockPos pos : BlockPos.betweenClosed(BX + 2, CAVE_FLOOR + 1, BZ + 2, BX + 13, CAVE_FLOOR + 5, BZ + 13)) {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
        // Suelo de tierra en una franja.
        for (BlockPos pos : BlockPos.betweenClosed(BX + 2, CAVE_FLOOR, BZ + 2, BX + 6, CAVE_FLOOR, BZ + 13)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // Hoyo cerrado de 2×2 y 1 de hondo.
        for (BlockPos pos : BlockPos.betweenClosed(BX + 10, CAVE_FLOOR, BZ + 10, BX + 11, CAVE_FLOOR, BZ + 11)) {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
        snapshot = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(BX + 1, CAVE_FLOOR - 1, BZ + 1, BX + 14, CAVE_FLOOR + 5, BZ + 14)) {
            snapshot.put(pos.immutable(), level.getBlockState(pos));
        }
        check("build test area", level.getBlockState(new BlockPos(BX + 4, CAVE_FLOOR, BZ + 4)).is(Blocks.DIRT),
                "la zona de pruebas no se construyó");
    }

    private static void checkFrontCodec(MinecraftServer server) {
        FrontSimulation sim = new FrontSimulation(7);
        sim.add(sim.create(FrontType.THUNDERSTORM, 12.5, -40));
        sim.add(sim.create(FrontType.FOG, -300, 77));
        FrontSavedData data = new FrontSavedData();
        data.capture(sim, false);
        Tag tag = FrontSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).result().orElseThrow();
        FrontSavedData back = FrontSavedData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        FrontSimulation restored = new FrontSimulation(1);
        back.restoreInto(restored);
        boolean ok = restored.fronts().size() == 2 && !back.controlsWeather()
                && restored.fronts().get(0).type() == FrontType.THUNDERSTORM
                && restored.fronts().get(1).x() == -300 && restored.windAngle() == sim.windAngle();
        check("front codec round trip", ok, "los datos guardados no coinciden: " + restored.fronts());
    }

    private static void checkPacket() {
        List<WeatherFront> fronts = List.of(new WeatherFront(5, FrontType.RAIN, 1, 2, 90, 0.5, -0.25, 10, 600));
        FrontsSyncPayload payload = new FrontsSyncPayload(true, 1.25, 0.75f, fronts);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buf);
        FrontsSyncPayload back = new FrontsSyncPayload(buf);
        FriendlyByteBuf again = new FriendlyByteBuf(Unpooled.buffer());
        back.write(again);
        boolean ok = buf.writerIndex() == again.writerIndex() && buf.readerIndex() == buf.writerIndex()
                && again.writerIndex() > 0 && equalBytes(buf, again);
        check("sync packet round trip", ok, "el paquete no se lee igual que se escribe");
    }

    private static boolean equalBytes(FriendlyByteBuf a, FriendlyByteBuf b) {
        for (int i = 0; i < a.writerIndex(); i++) {
            if (a.getByte(i) != b.getByte(i)) {
                return false;
            }
        }
        return true;
    }

    private static void checkCommands(MinecraftServer server) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput()
                .withPosition(new Vec3(8, 0, 8));
        FrontSimulation sim = FrontTicker.simulation(server);
        server.getCommands().performPrefixedCommand(source, "stormroot front clear");
        boolean cleared = sim.fronts().isEmpty();
        server.getCommands().performPrefixedCommand(source, "stormroot front spawn rain");
        boolean spawned = sim.fronts().size() == 1 && sim.fronts().get(0).type() == FrontType.RAIN;
        server.getCommands().performPrefixedCommand(source, "stormroot seep set 0.5");
        SeepageCell c = SeepageSavedData.get(server).field().get(0, 0);
        boolean seep = c != null && Math.abs(c.deep() - 0.5) < 1e-6;
        server.getCommands().performPrefixedCommand(source, "stormroot here");
        server.getCommands().performPrefixedCommand(source, "stormroot seep");
        server.getCommands().performPrefixedCommand(source, "stormroot front clear");
        c.set(0);
        check("commands", cleared && spawned && seep && sim.fronts().isEmpty(),
                "clear=" + cleared + " spawn=" + spawned + " seep=" + seep);
    }

    private static void checkSettings(MinecraftServer server) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "stormroot settings cave_changes off");
        server.getCommands().performPrefixedCommand(source, "stormroot settings lightning_roots off");
        boolean off = !io.github.hcuadrado428.stormroot.config.StormrootConfig.caveChangesActive()
                && io.github.hcuadrado428.stormroot.config.StormrootConfig.effectiveRootChance() == 0;
        FrontSavedData data = FrontSavedData.get(server);
        Tag tag = FrontSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).result().orElseThrow();
        boolean saved = tag.toString().contains("cave_changes:0b") || tag.toString().contains("cave_changes:false");
        server.getCommands().performPrefixedCommand(source, "stormroot settings cave_changes on");
        server.getCommands().performPrefixedCommand(source, "stormroot settings lightning_roots on");
        boolean on = io.github.hcuadrado428.stormroot.config.StormrootConfig.caveChangesActive()
                && io.github.hcuadrado428.stormroot.config.StormrootConfig.effectiveRootChance() > 0;
        check("world settings", off && saved && on, "apagar=" + off + ", guardado=" + saved + " (" + tag + "), encender=" + on);
    }

    private static void checkWeatherCommand(MinecraftServer server) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        ServerLevel level = server.overworld();
        FrontTicker.simulation(server).clear();
        FrontTicker.applyNow(server); // Stormroot pone el clima (despejado)
        // level.isRaining() es la intensidad visual (sube tick a tick); el estado del clima está en los datos del mundo.
        net.minecraft.world.level.storage.ServerLevelData data = server.getWorldData().overworldData();
        server.getCommands().performPrefixedCommand(source, "weather rain 600");
        FrontTicker.applyNow(server);
        boolean respected = data.isRaining() && FrontTicker.weatherOverridden() && !FrontTicker.controlsWeatherNow();
        server.getCommands().performPrefixedCommand(source, "weather clear 1");
        FrontTicker.applyNow(server);
        boolean back = !FrontTicker.weatherOverridden() && !data.isRaining();
        check("/weather is respected", respected && back, "respeta /weather=" + respected + ", retoma el control=" + back);
    }

    private static void checkStrayLightning(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FrontSimulation sim = FrontTicker.simulation(server);
        FrontTicker.setControlsWeather(true);
        FrontTicker.applyNow(server);
        sim.clear();
        sim.add(new WeatherFront(901, FrontType.THUNDERSTORM, 0, 0, 100, 0, 0, 500, 1000));
        level.setWeatherParameters(0, 100, true, true); // tormenta vanilla corta (no cuenta como /weather)
        level.setRainLevel(1f); // la intensidad visual sube tick a tick: aquí se fija ya
        level.setThunderLevel(1f);
        //? if >=1.21.2 {
        LightningBolt inside = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        LightningBolt outside = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        //?} else {
        /*LightningBolt inside = EntityType.LIGHTNING_BOLT.create(level);
        LightningBolt outside = EntityType.LIGHTNING_BOLT.create(level);
        *///?}
        inside.setPos(10, 64, 10);
        outside.setPos(3000, 64, 3000);
        boolean keepInside = !io.github.hcuadrado428.stormroot.weather.LightningCrystals.isStrayStormLightning(inside, level);
        boolean removeOutside = io.github.hcuadrado428.stormroot.weather.LightningCrystals.isStrayStormLightning(outside, level);
        level.setWeatherParameters(100, 0, false, false);
        level.setRainLevel(0f);
        level.setThunderLevel(0f);
        boolean keepWhenClear = !io.github.hcuadrado428.stormroot.weather.LightningCrystals.isStrayStormLightning(outside, level);
        sim.clear();
        check("stray storm lightning is removed", keepInside && removeOutside && keepWhenClear,
                "dentro se queda=" + keepInside + ", fuera se quita=" + removeOutside + ", sin tormenta se queda=" + keepWhenClear);
    }

    private static void checkForecast() {
        FrontSimulation sim = new FrontSimulation(3);
        sim.add(new WeatherFront(1, FrontType.THUNDERSTORM, -300, 0, 100, 2, 0, 500, 1000));
        FrontSimulation.Forecast forecast = sim.forecast(0, 0, 180, 5, 0.15);
        boolean ok = forecast != null && forecast.etaSeconds() > 60 && forecast.etaSeconds() < 150
                && sim.forecast(0, 5000, 180, 5, 0.15) == null && sim.fronts().get(0).x() == -300;
        check("forecast", ok, "pronóstico inesperado: " + forecast);
    }

    private static void checkWeathervane(MinecraftServer server, ServerLevel level) {
        double wind = FrontTicker.simulation(server).windAngle();
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        StringBuilder why = new StringBuilder();
        boolean ok = true;
        for (int i = 0; i < sides.length; i++) {
            BlockPos pos = new BlockPos(BX + 1 + i, Y_TOP + 1, BZ + 1);
            BlockState state = ModBlocks.WEATHERVANE.defaultBlockState().setValue(InstrumentBlock.FACING, sides[i]);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            ((InstrumentBlock) ModBlocks.WEATHERVANE).tick(level.getBlockState(pos), level, pos, level.getRandom());
            int power = level.getBlockState(pos).getValue(InstrumentBlock.POWER);
            double facing = Math.atan2(sides[i].getStepZ(), sides[i].getStepX());
            int expected = (int) Math.round(15 * Math.max(0, Math.cos(wind - facing)));
            if (power != expected) {
                ok = false;
                why.append(sides[i]).append(": ").append(power).append(" != ").append(expected).append("; ");
            }
            if (level.getSignal(pos, Direction.DOWN) != expected) {
                ok = false;
                why.append(sides[i]).append(" no emite la señal; ");
            }
        }
        check("weathervane", ok, why.toString());
    }

    private static void checkBarometer(MinecraftServer server, ServerLevel level) {
        FrontSimulation sim = FrontTicker.simulation(server);
        BlockPos pos = new BlockPos(BX + 6, Y_TOP + 1, BZ + 1);
        level.setBlock(pos, ModBlocks.BAROMETER.defaultBlockState(), Block.UPDATE_ALL);
        InstrumentBlock barometer = (InstrumentBlock) ModBlocks.BAROMETER;
        sim.clear();
        sim.add(new WeatherFront(900, FrontType.THUNDERSTORM, pos.getX(), pos.getZ(), 100, 0, 0, 500, 1000));
        barometer.tick(level.getBlockState(pos), level, pos, level.getRandom());
        int under = level.getBlockState(pos).getValue(InstrumentBlock.POWER);
        sim.clear();
        barometer.tick(level.getBlockState(pos), level, pos, level.getRandom());
        int clear = level.getBlockState(pos).getValue(InstrumentBlock.POWER);
        check("barometer", under == 15 && clear == 0, "bajo tormenta=" + under + ", despejado=" + clear);
    }

    private static void checkPool(ServerLevel level) {
        cell = SeepageSavedData.get(level.getServer()).field().getOrCreate(CX, CZ);
        cell.set(1);
        boolean open = SeepageTicker.fillPoolForTest(level, cell, new BlockPos(BX + 4, CAVE_FLOOR + 1, BZ + 8), 256);
        boolean hole = SeepageTicker.fillPoolForTest(level, cell, new BlockPos(BX + 10, CAVE_FLOOR, BZ + 10), 256);
        boolean filled = level.getBlockState(new BlockPos(BX + 11, CAVE_FLOOR, BZ + 11)).is(Blocks.WATER);
        check("pool fills a closed hole only", !open && hole && filled && cell.count(SeepageCell.Kind.POOL) == 4,
                "suelo abierto=" + open + ", hoyo=" + hole + ", agua=" + filled + ", pozas=" + cell.count(SeepageCell.Kind.POOL));
    }

    private static void floodCave(ServerLevel level) {
        for (int i = 0; i < 400; i++) {
            cell.set(1);
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        int puddles = cell.count(SeepageCell.Kind.PUDDLE);
        int mud = cell.count(SeepageCell.Kind.MUD);
        int moss = cell.count(SeepageCell.Kind.MOSS);
        Stormroot.LOGGER.info("[selftest] cueva empapada: charcos {}, pozas {}, barro {}, musgo {}",
                puddles, cell.count(SeepageCell.Kind.POOL), mud, moss);
        check("seepage floods the cave", puddles > 0 && mud > 0 && moss > 0 && puddles <= 256 * 0.6,
                "charcos=" + puddles + ", barro=" + mud + ", musgo=" + moss);
        boolean consistent = true;
        for (SeepageCell.Change change : cell.changes()) {
            BlockState state = level.getBlockState(BlockPos.of(change.pos()));
            boolean match = switch (change.kind()) {
                case PUDDLE, POOL -> state.is(Blocks.WATER);
                case MUD -> state.is(Blocks.MUD);
                case MOSS -> state.is(Blocks.MOSS_BLOCK);
                case CRACKED -> state.is(Blocks.COARSE_DIRT);
            };
            consistent &= match;
        }
        check("changes match the world", consistent, "algún cambio apuntado no está en el mundo");
    }

    private static void checkContained(ServerLevel level) {
        int flowing = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BX, CAVE_FLOOR - 2, BZ, BX + 15, CAVE_FLOOR + 6, BZ + 15)) {
            if (!level.getFluidState(pos).isEmpty() && !level.getFluidState(pos).isSource()) {
                flowing++;
            }
        }
        check("water stays contained", flowing == 0, flowing + " bloque(s) de agua corriendo: algún charco se derrama");
    }

    private static void checkRoot(ServerLevel level) {
        int x = BX + 8;
        int z = BZ + 8;
        int roots = 0;
        for (int y = Y_TOP; y > CAVE_FLOOR + 5; y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.LIGHTNING_ROOT)) {
                roots++;
            }
        }
        int expected = Y_TOP - (CAVE_FLOOR + 5);
        boolean stopped = level.getBlockState(new BlockPos(x, CAVE_FLOOR + 5, z)).isAir();
        check("lightning root grows to the cave", roots == expected && stopped,
                roots + "/" + expected + " bloques de raíz, se paró en la cueva=" + stopped);
    }

    private static void checkSeepageCodec(MinecraftServer server) {
        SeepageSavedData data = SeepageSavedData.get(server);
        Tag tag = SeepageSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).result().orElseThrow();
        SeepageSavedData back = SeepageSavedData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        SeepageCell copy = back.field().get(CX, CZ);
        boolean ok = copy != null && copy.changes().equals(cell.changes()) && copy.deep() == cell.deep();
        check("seepage codec round trip", ok, "los datos de filtración guardados no coinciden");
    }

    private static void dryCave(ServerLevel level) {
        for (int i = 0; i < 3000 && hasWaterOrMud(); i++) {
            cell.set(-1);
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        boolean noWater = !hasWaterOrMud();
        for (int i = 0; i < 3000 && !cell.changes().isEmpty(); i++) {
            cell.set(0.3); // entre "secándose" y "barro": deshace las grietas y no crea nada nuevo
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        List<String> different = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            BlockState now = level.getBlockState(entry.getKey());
            if (!now.equals(entry.getValue()) && different.size() < 5) {
                different.add(entry.getKey().toShortString() + " " + CaveMaterials.idOf(entry.getValue()) + " -> " + now);
            }
        }
        check("seepage dries and restores every block", noWater && cell.changes().isEmpty() && different.isEmpty(),
                "sin agua=" + noWater + ", cambios pendientes=" + cell.changes().size() + ", distintos=" + different);
        cell.set(0);
    }

    private static boolean hasWaterOrMud() {
        return cell.count(SeepageCell.Kind.PUDDLE) + cell.count(SeepageCell.Kind.POOL) + cell.count(SeepageCell.Kind.MUD)
                + cell.count(SeepageCell.Kind.MOSS) > 0;
    }

    private static void finish(MinecraftServer server) {
        serverDone = true;
        stage = 99;
        server.overworld().setChunkForced(CX, CZ, false);
        Stormroot.LOGGER.info("[selftest] server done: {} passed, {} failed", PASSED.get(), FAILED.get());
        if (MODE.equals("server") && exitWhenDone()) {
            server.halt(false);
        }
    }

    /** Cuentas globales (las usa también el self-test del cliente para su resumen). */
    public static int passed() {
        return PASSED.get();
    }

    public static int failed() {
        return FAILED.get();
    }
}
