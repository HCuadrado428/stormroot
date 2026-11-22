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

    private static final MyceliumSelfTest MYCELIUM = new MyceliumSelfTest(BX, BZ);
    private static final FrostSelfTest FROST = new FrostSelfTest(BX, BZ, Y_TOP);
    private static final SandSelfTest SAND = new SandSelfTest(BX, BZ, Y_TOP);
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
                run("/weather moves fronts", () -> checkWeatherCommand(server));
                run("sleeping clears the storm overhead", () -> checkSleepClearsWeather(server));
                run("weather cycle rule freezes fronts", () -> checkWeatherCycleRule(server));
                run("stray storm lightning is removed", () -> checkStrayLightning(server));
                run("forecast", SelfTest::checkForecast);
                run("rain falls only under rain clouds", () -> checkRainUnderClouds(server));
                run("fronts are born by biome", () -> checkFrontsByBiome(level));
                run("fog dampens without puddles", SelfTest::checkFogDamp);
                run("blizzards pile up snow", () -> checkBlizzardSnow(level));
                run("weathervane", () -> checkWeathervane(server, level));
                run("barometer", () -> checkBarometer(server, level));
                run("pool fills a closed hole only", () -> checkPool(level));
                run("seepage floods the cave", () -> floodCave(level));
                run("lightning root starts", () -> LightningRoots.start(level, new BlockPos(BX + 8, Y_TOP, BZ + 8)));
                run("dry storm bolts start", () -> strikeFireTest(level));
                run("storm fire starts", () -> startStormFire(level));
                run("mycelium tunnel", () -> MYCELIUM.build(level));
                run("frost pools", () -> FROST.build(level));
                run("sand corner", () -> SAND.build(level));
                stage = 1;
                waitUntil = ticks + 160;
            }
            case 1 -> {
                run("water stays contained", () -> checkContained(level));
                run("dry storm lightning does not burn", () -> checkFireTest(level));
                run("storm fire only burns tree leaves", () -> checkStormFire(level));
                run("lightning root grows to the cave", () -> checkRoot(level));
                run("seepage codec round trip", () -> checkSeepageCodec(server));
                run("seepage dries and restores every block", () -> dryCave(level));
                run("ice cave freezes and thaws back", () -> iceCave(level));
                run("monsoon floods banks and caves and recedes", () -> monsoonFlood(level));
                run("massive thaw floods the cave", SelfTest::checkMassiveThaw);
                run("heat waves dry the ground and clear the sky", SelfTest::checkHeatWave);
                run("one in three storms brings hail", SelfTest::checkHailClouds);
                run("magnetic storms magnetize iron and auroras leave dust", () -> checkRareSkies(level));
                run("the end of the rainbow has a treasure", () -> checkRainbowTreasure(level));
                run("hail hurts crops and glass and leaves hailstones", () -> checkHailGround(level));
                run("mycelium network", () -> MYCELIUM.run(level));
                run("frost and aurora", () -> FROST.run(level));
                run("wind", WindSelfTest::run);
                run("front collisions", CollisionSelfTest::run);
                run("sandstorm", () -> SAND.run(level));
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
                && ModBlocks.LIGHTNING_ROOT_ITEM != null && ModBlocks.MYCELIUM_VEIN != null && ModBlocks.MYCELIUM_NODE != null
                && ModBlocks.MYCELIUM_NODE_ITEM != null && ModItems.SPORES != null && ModBlocks.FROST_ICE != null && ModBlocks.SAND_LAYER != null && ModItems.SAND_GOGGLES != null
                && io.github.hcuadrado428.stormroot.entity.ModEntities.SPORELING != null
                && io.github.hcuadrado428.stormroot.entity.ModEntities.WEAVER != null && ModItems.MYCELIUM_THREAD != null
                && io.github.hcuadrado428.stormroot.entity.ModEntities.GUARDIAN != null && ModItems.MYCELIUM_HEART != null;
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
        FrontsSyncPayload payload = new FrontsSyncPayload(true, 1.25, 0.75f, 0x5EED_CAFEL, fronts,
                List.of(new io.github.hcuadrado428.stormroot.sky.Rainbow(7, 10.5, 64, -3.5, 1.0, 100, 3100)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buf);
        FrontsSyncPayload back = new FrontsSyncPayload(buf);
        FriendlyByteBuf again = new FriendlyByteBuf(Unpooled.buffer());
        back.write(again);
        boolean ok = buf.writerIndex() == again.writerIndex() && buf.readerIndex() == buf.writerIndex()
                && again.writerIndex() > 0 && equalBytes(buf, again) && back.skySeed() == 0x5EED_CAFEL
                && back.rainbows().size() == 1 && back.rainbows().get(0).endTick() == 3100;
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
        FrontSimulation sim = FrontTicker.simulation(server);
        FrontTicker.setControlsWeather(true);
        FrontTicker.resumeSpawnsForTest();
        sim.clear();
        FrontTicker.applyNow(server); // Stormroot pone el clima (despejado)
        // level.isRaining() es la intensidad visual (sube tick a tick); el estado del clima está en los datos del mundo.
        net.minecraft.world.level.storage.ServerLevelData data = server.getWorldData().overworldData();
        // /weather rain 600 (600 ticks = 30 s): nace un frente de lluvia que dura 30 s y Stormroot sigue al mando.
        server.getCommands().performPrefixedCommand(source, "weather rain 600");
        WeatherFront rain = sim.fronts().stream().filter(f -> f.type() == FrontType.RAIN).findFirst().orElse(null);
        boolean rainOk = rain != null && Math.abs(rain.maxAgeSeconds() - rain.ageSeconds() - 30.0) < 0.01 && rain.lifeFactor() > 0.99
                && !FrontTicker.weatherOverridden();
        server.getCommands().performPrefixedCommand(source, "weather thunder 600");
        boolean thunderOk = sim.fronts().stream().anyMatch(f -> f.type() == FrontType.THUNDERSTORM);
        // /weather clear: todos se deshacen, no nacen nuevos y deja de llover ya.
        server.getCommands().performPrefixedCommand(source, "weather clear 6000");
        boolean fading = !sim.fronts().isEmpty()
                && sim.fronts().stream().allMatch(f -> f.lifeFactor() < 1.0 && FrontTicker.isDissipating(f));
        boolean clearOk = fading && FrontTicker.spawnsPaused(server.overworld()) && !data.isRaining();
        FrontTicker.resumeSpawnsForTest();
        sim.clear();
        FrontTicker.applyNow(server);
        check("/weather moves fronts", rainOk && thunderOk && clearOk,
                "rain=" + rainOk + ", thunder=" + thunderOk + ", clear=" + clearOk + " (se deshacen=" + fading + ")");
    }

    private static void checkSleepClearsWeather(MinecraftServer server) {
        FrontSimulation sim = FrontTicker.simulation(server);
        FrontTicker.resumeSpawnsForTest();
        sim.clear();
        WeatherFront over = new WeatherFront(911, FrontType.THUNDERSTORM, 0, 0, 100, 0, 0, 500, 1000);
        WeatherFront far = new WeatherFront(912, FrontType.RAIN, 5000, 0, 100, 0, 0, 500, 1000);
        sim.add(over);
        sim.add(far);
        // Los que duermen están en (10, 10): se deshace la tormenta que tienen encima, la lejana no.
        int n = FrontTicker.dissipateNear(server, List.of(new double[] {10, 10}));
        boolean ok = n == 1 && over.lifeFactor() < 1.0 && FrontTicker.isDissipating(over)
                && far.lifeFactor() == 1.0 && !FrontTicker.isDissipating(far);
        FrontTicker.resumeSpawnsForTest();
        sim.clear();
        FrontTicker.applyNow(server);
        check("sleeping clears the storm overhead", ok, "deshechos=" + n + ", encima=" + over.lifeFactor() + ", lejos=" + far.lifeFactor());
    }

    private static void checkWeatherCycleRule(MinecraftServer server) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        FrontSimulation sim = FrontTicker.simulation(server);
        sim.clear();
        WeatherFront front = new WeatherFront(921, FrontType.RAIN, 300, 300, 100, 0.5, 0, 500, 1000);
        sim.add(front);
        //? if >=1.21.11 {
        String rule = "advance_weather";
        //?} else {
        /*String rule = "doWeatherCycle";
        *///?}
        server.getCommands().performPrefixedCommand(source, "gamerule " + rule + " false");
        boolean frozenRule = !FrontTicker.advancesWeather(server.overworld());
        double age = front.ageSeconds();
        double x = front.x();
        FrontTicker.step(server);
        boolean frozen = front.ageSeconds() == age && front.x() == x;
        server.getCommands().performPrefixedCommand(source, "gamerule " + rule + " true");
        FrontTicker.step(server);
        boolean moves = FrontTicker.advancesWeather(server.overworld()) && front.ageSeconds() > age;
        sim.clear();
        FrontTicker.applyNow(server);
        check("weather cycle rule freezes fronts", frozenRule && frozen && moves,
                "regla=" + frozenRule + ", congelado=" + frozen + ", vuelve a moverse=" + moves);
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

    /** Lluvia solo bajo el nubarrón: la precipitación del bioma (lo que usan fuegos, calderos y gotas) se apaga fuera. */
    private static void checkRainUnderClouds(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FrontSimulation sim = FrontTicker.simulation(server);
        FrontTicker.setControlsWeather(true);
        sim.clear();
        sim.add(new WeatherFront(902, FrontType.RAIN, 0, 0, 120, 0, 0, 500, 1000));
        BlockPos under = new BlockPos(0, 70, 0);
        BlockPos away = new BlockPos(3000, 70, 3000);
        net.minecraft.world.level.biome.Biome.Precipitation atUnder = precipitation(level, under);
        net.minecraft.world.level.biome.Biome.Precipitation atAway = precipitation(level, away);
        sim.clear();
        check("rain falls only under rain clouds",
                atUnder == net.minecraft.world.level.biome.Biome.Precipitation.RAIN
                        && atAway == net.minecraft.world.level.biome.Biome.Precipitation.NONE,
                "bajo la nube=" + atUnder + ", lejos=" + atAway);
    }

    private static net.minecraft.world.level.biome.Biome.Precipitation precipitation(ServerLevel level, BlockPos pos) {
        return io.github.hcuadrado428.stormroot.weather.BiomeClimate.precipitationAt(level, pos);
    }

    /** El mundo plano es de llanuras (templado); en zona seca solo nacen tormentas (secas) y olas de calor; nunca niebla en el desierto. */
    private static void checkFrontsByBiome(ServerLevel level) {
        io.github.hcuadrado428.stormroot.weather.ClimateZone here =
                io.github.hcuadrado428.stormroot.weather.BiomeClimate.zoneAt(level, BX + 8, BZ + 8);
        FrontSimulation sim = new FrontSimulation(7);
        boolean dryOnlyStorms = true;
        boolean temperateHasRain = false;
        boolean blizzardOnlyCold = true;
        boolean coldHasBlizzard = false;
        boolean monsoonInHumid = false;
        for (int i = 0; i < 200; i++) {
            blizzardOnlyCold &= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.TEMPERATE) != FrontType.BLIZZARD
                    && sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.DRY) != FrontType.BLIZZARD;
            coldHasBlizzard |= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.COLD) == FrontType.BLIZZARD;
            blizzardOnlyCold &= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.TEMPERATE) != FrontType.MONSOON;
            monsoonInHumid |= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.HUMID) == FrontType.MONSOON;
            blizzardOnlyCold &= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.HUMID) != FrontType.AURORA;
            FrontType dryType = sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.DRY);
            dryOnlyStorms &= dryType == FrontType.THUNDERSTORM || dryType == FrontType.HEAT_WAVE
                    || dryType == FrontType.MAGNETIC_STORM || dryType == FrontType.SANDSTORM;
            blizzardOnlyCold &= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.TEMPERATE) != FrontType.SANDSTORM;
            temperateHasRain |= sim.typeFor(io.github.hcuadrado428.stormroot.weather.ClimateZone.TEMPERATE) == FrontType.RAIN;
        }
        check("fronts are born by biome",
                here == io.github.hcuadrado428.stormroot.weather.ClimateZone.TEMPERATE && dryOnlyStorms && temperateHasRain
                        && blizzardOnlyCold && coldHasBlizzard && monsoonInHumid,
                "zona del mundo plano=" + here + ", en seco solo tormentas, calor, arena y magnéticas=" + dryOnlyStorms + ", lluvia en templado="
                        + temperateHasRain + ", ventiscas y monzones solo donde tocan=" + blizzardOnlyCold + ", ventiscas en frío=" + coldHasBlizzard
                        + ", monzones en húmedos=" + monsoonInHumid);
    }

    /** La nieve de ventisca se apila hasta 4 capas y, sin ventisca, baja hasta 1 (la de vanilla). */
    private static void checkBlizzardSnow(ServerLevel level) {
        BlockPos above = new BlockPos(BX + 12, Y_TOP + 1, BZ + 6);
        level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int i = 0; i < 10; i++) {
            io.github.hcuadrado428.stormroot.weather.BlizzardSnow.addLayer(level, above);
        }
        BlockState piled = level.getBlockState(above);
        int layers = piled.is(Blocks.SNOW) ? piled.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) : 0;
        for (int i = 0; i < 10; i++) {
            io.github.hcuadrado428.stormroot.weather.BlizzardSnow.melt(level, above.above());
        }
        BlockState melted = level.getBlockState(above);
        int left = melted.is(Blocks.SNOW) ? melted.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) : 0;
        level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        check("blizzards pile up snow", layers == io.github.hcuadrado428.stormroot.weather.BlizzardSnow.MAX_LAYERS && left == 1,
                "capas tras la ventisca=" + layers + ", capas tras derretirse=" + left);
    }

    /** La niebla humedece hasta FOG_CAP (barro y musgo sí, charcos no) y no seca mientras está encima. */
    private static void checkFogDamp() {
        SeepageCell cell = new SeepageCell();
        io.github.hcuadrado428.stormroot.seep.SeepageRates rates = io.github.hcuadrado428.stormroot.seep.SeepageRates.DEFAULT;
        for (int i = 0; i < 400; i++) {
            cell.update(0, 1.0, 60, rates);
        }
        boolean ok = cell.foggy() && cell.surface() <= SeepageCell.FOG_CAP + 1e-9
                && cell.deep() >= SeepageTicker.FOG_MOSS_AT && cell.deep() < SeepageTicker.PUDDLE_AT;
        check("fog dampens without puddles", ok, "superficie=" + cell.surface() + ", subsuelo=" + cell.deep());
    }

    private static final BlockPos DRY_BOLT = new BlockPos(BX + 14, Y_TOP + 1, BZ + 14);
    private static final BlockPos NORMAL_BOLT = new BlockPos(BX + 14, Y_TOP + 1, BZ + 10);

    /** Dos rayos sobre netherrack (donde el fuego no se apaga): uno de tormenta seca y uno normal. */
    private static void strikeFireTest(ServerLevel level) {
        for (BlockPos pos : new BlockPos[] {DRY_BOLT, NORMAL_BOLT}) {
            level.setBlock(pos.below(), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_CLIENTS);
            //? if >=1.21.2 {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            //?} else {
            /*LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            *///?}
            bolt.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (pos.equals(DRY_BOLT)) {
                io.github.hcuadrado428.stormroot.weather.DryStorms.markForTest(bolt);
            }
            level.addFreshEntity(bolt);
        }
    }

    /** El rayo normal deja fuego; el de tormenta seca, no. Luego se limpia. */
    private static void checkFireTest(ServerLevel level) {
        boolean dryFire = level.getBlockState(DRY_BOLT).is(Blocks.FIRE);
        boolean normalFire = level.getBlockState(NORMAL_BOLT).is(Blocks.FIRE);
        for (BlockPos pos : new BlockPos[] {DRY_BOLT, NORMAL_BOLT}) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // Desde 1.21.11 el fuego solo prende cerca de los jugadores (canSpreadFireAround): sin jugadores cerca, el rayo
        // normal tampoco deja fuego y solo se puede comprobar que el de tormenta seca no lo deja.
        //? if >=1.21.11 {
        boolean fireAllowed = level.canSpreadFireAround(NORMAL_BOLT);
        //?} else {
        /*boolean fireAllowed = true;
        *///?}
        check("dry storm lightning does not burn", !dryFire && (normalFire || !fireAllowed),
                "fuego del rayo de tormenta seca=" + dryFire + ", fuego del rayo normal=" + normalFire
                        + ", el fuego puede prender aquí=" + fireAllowed);
    }

    private static final BlockPos FIRE_CENTER = new BlockPos(BX + 3, Y_TOP + 1, BZ + 13);
    private static final BlockPos FIRE_PLANKS = new BlockPos(BX + 3, Y_TOP + 1, BZ + 11);
    private static final BlockPos FIRE_PERSISTENT = new BlockPos(BX + 5, Y_TOP + 1, BZ + 13);

    /** Una copa de hojas 3×3, una hoja puesta por un jugador y una tabla de madera al lado; fuego del clima en el centro. */
    private static void startStormFire(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(BX + 2, Y_TOP + 1, BZ + 12, BX + 4, Y_TOP + 1, BZ + 14)) {
            level.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(FIRE_PERSISTENT, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true), Block.UPDATE_CLIENTS);
        level.setBlock(FIRE_PLANKS, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(FIRE_CENTER, io.github.hcuadrado428.stormroot.registry.ModBlocks.STORM_FIRE.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** La madera y la hoja del jugador siguen ahí, no hay fuego vanilla y (si el fuego puede prender) arden más hojas. */
    private static void checkStormFire(ServerLevel level) {
        boolean planks = level.getBlockState(FIRE_PLANKS).is(Blocks.OAK_PLANKS);
        boolean persistent = level.getBlockState(FIRE_PERSISTENT).is(Blocks.OAK_LEAVES);
        boolean centerBurnt = !level.getBlockState(FIRE_CENTER).is(Blocks.OAK_LEAVES);
        int vanillaFire = 0;
        int burnt = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BX + 1, Y_TOP + 1, BZ + 10, BX + 6, Y_TOP + 3, BZ + 15)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE)) {
                vanillaFire++;
            }
            if (pos.getY() == Y_TOP + 1 && pos.getX() >= BX + 2 && pos.getX() <= BX + 4 && pos.getZ() >= BZ + 12
                    && !pos.equals(FIRE_CENTER) && !state.is(Blocks.OAK_LEAVES)) {
                burnt++;
            }
        }
        boolean allowed = io.github.hcuadrado428.stormroot.block.StormFireBlock.fireAllowed(level, FIRE_CENTER);
        for (BlockPos pos : BlockPos.betweenClosed(BX + 1, Y_TOP + 1, BZ + 10, BX + 6, Y_TOP + 3, BZ + 15)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        check("storm fire only burns tree leaves",
                planks && persistent && centerBurnt && vanillaFire == 0 && (burnt > 0 || !allowed),
                "madera=" + planks + ", hoja del jugador=" + persistent + ", centro quemado=" + centerBurnt
                        + ", fuego vanilla=" + vanillaFire + ", hojas quemadas=" + burnt + ", el fuego puede prender=" + allowed);
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
        // Primero con niebla y humedad media (barro y musgo, sin charcos): así los charcos no se quedan con toda la roca
        // antes de que el azar dé musgo (1 de cada 6 intentos). Después, empapada: 400 rondas.
        cell.setFoggyForTest(true);
        for (int i = 0; i < 400 && cell.count(SeepageCell.Kind.MOSS) == 0; i++) {
            cell.set((SeepageTicker.FOG_MOSS_AT + SeepageTicker.PUDDLE_AT) / 2);
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        cell.setFoggyForTest(false);
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
                default -> true;
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

    /** Cueva helada: nieve, hielo, carámbanos (hasta 3) y cristales; sin charcos; al deshelarse vuelve todo. */
    private static void iceCave(ServerLevel level) {
        cell.set(0);
        cell.setFrost(1);
        for (int i = 0; i < 800; i++) {
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        int floor = cell.count(SeepageCell.Kind.SNOW_FLOOR) + cell.count(SeepageCell.Kind.ICE_FLOOR);
        int icicles = cell.count(SeepageCell.Kind.ICICLE);
        int crystals = cell.count(SeepageCell.Kind.ICE_CRYSTAL);
        boolean dry = cell.count(SeepageCell.Kind.PUDDLE) + cell.count(SeepageCell.Kind.POOL) + cell.count(SeepageCell.Kind.MUD) == 0;
        int longest = 0;
        for (SeepageCell.Change change : cell.changes()) {
            if (change.kind() == SeepageCell.Kind.ICICLE) {
                longest = Math.max(longest, io.github.hcuadrado428.stormroot.block.IcicleBlock.lengthAt(level, BlockPos.of(change.pos())));
            }
        }
        cell.setFrost(0);
        for (int i = 0; i < 4000 && !cell.changes().isEmpty(); i++) {
            cell.set(0.3);
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        List<String> different = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            BlockState now = level.getBlockState(entry.getKey());
            if (!now.equals(entry.getValue()) && different.size() < 5) {
                different.add(entry.getKey().toShortString() + " " + CaveMaterials.idOf(entry.getValue()) + " -> " + now);
            }
        }
        check("ice cave freezes and thaws back",
                floor > 0 && icicles > 0 && crystals > 0 && dry && longest <= io.github.hcuadrado428.stormroot.block.IcicleBlock.MAX_LENGTH
                        && cell.changes().isEmpty() && different.isEmpty(),
                "suelo helado=" + floor + ", carámbanos=" + icicles + " (el más largo " + longest + "), cristales=" + crystals
                        + ", sin charcos=" + dry + ", cambios pendientes=" + cell.changes().size() + ", distintos=" + different);
        cell.set(0);
    }

    /** La ola de calor seca el suelo mucho más rápido que el buen tiempo y casi no deja nubes de buen tiempo. */
    private static void checkHeatWave() {
        io.github.hcuadrado428.stormroot.seep.SeepageRates rates = io.github.hcuadrado428.stormroot.seep.SeepageRates.DEFAULT;
        SeepageCell normal = new SeepageCell(0.8, 0.8, List.of());
        SeepageCell hot = new SeepageCell(0.8, 0.8, List.of());
        for (int i = 0; i < 20; i++) {
            normal.update(0, 30, rates);
            hot.update(0, 30, rates);
            hot.applyHeat(1.0, 30, rates);
        }
        boolean drier = hot.surface() < normal.surface() - 0.2;
        List<WeatherFront> heat = List.of(new WeatherFront(903, FrontType.HEAT_WAVE, 0, 0, 300, 0, 0, 1000, 2000));
        int clear = 0;
        int calm = 0;
        long seed = io.github.hcuadrado428.stormroot.sky.CloudCover.SKY_SEED;
        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                double x = i * 12;
                double z = j * 12;
                if (io.github.hcuadrado428.stormroot.sky.CloudCover.at(seed, x, z, 0, 100, List.of())
                        == io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.FAIR) {
                    calm++;
                }
                if (io.github.hcuadrado428.stormroot.sky.CloudCover.at(seed, x, z, 0, 100, heat)
                        == io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.FAIR) {
                    clear++;
                }
            }
        }
        check("heat waves dry the ground and clear the sky", drier && clear <= calm / 3,
                "humedad tras 10 min: normal=" + normal.surface() + ", con calor=" + hot.surface()
                        + "; nubes de buen tiempo sin/con calor=" + calm + "/" + clear);
    }

    /** El final del arcoíris: un fragmento de arcoíris y de 1 a 3 esmeraldas. */
    private static void checkRainbowTreasure(ServerLevel level) {
        BlockPos pos = new BlockPos(BX + 13, Y_TOP + 1, BZ + 3);
        io.github.hcuadrado428.stormroot.weather.Rainbows.reward(level, pos);
        List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3), e -> true);
        int shards = 0;
        int emeralds = 0;
        for (net.minecraft.world.entity.item.ItemEntity item : items) {
            if (item.getItem().is(io.github.hcuadrado428.stormroot.registry.ModItems.RAINBOW_SHARD)) {
                shards += item.getItem().getCount();
            } else if (item.getItem().is(net.minecraft.world.item.Items.EMERALD)) {
                emeralds += item.getItem().getCount();
            }
            item.discard();
        }
        check("the end of the rainbow has a treasure", shards == 1 && emeralds >= 1 && emeralds <= 3,
                "fragmentos=" + shards + ", esmeraldas=" + emeralds);
    }

    /** El hierro se magnetiza (y nada más); la aurora deja polvo en el suelo. */
    private static void checkRareSkies(ServerLevel level) {
        BlockPos ore = new BlockPos(BX + 12, Y_TOP + 1, BZ + 12);
        BlockPos other = ore.east();
        level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(other, Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        boolean magnetized = io.github.hcuadrado428.stormroot.weather.RareSkies.magnetize(level, ore)
                && level.getBlockState(ore).is(io.github.hcuadrado428.stormroot.registry.ModBlocks.MAGNETIC_ROCK);
        boolean coalSafe = !io.github.hcuadrado428.stormroot.weather.RareSkies.magnetize(level, other)
                && level.getBlockState(other).is(Blocks.COAL_ORE);
        io.github.hcuadrado428.stormroot.weather.RareSkies.dropDust(level, ore.above());
        List<net.minecraft.world.entity.item.ItemEntity> dust = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(ore).inflate(3),
                e -> e.getItem().is(io.github.hcuadrado428.stormroot.registry.ModItems.AURORA_DUST));
        int count = dust.size();
        dust.forEach(net.minecraft.world.entity.Entity::discard);
        level.setBlock(ore, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        check("magnetic storms magnetize iron and auroras leave dust", magnetized && coalSafe && count == 1,
                "hierro magnetizado=" + magnetized + ", carbón intacto=" + coalSafe + ", polvo de aurora=" + count);
    }

    /** El núcleo de las tormentas con id múltiplo de 3 es de granizo; el resto, tormenta normal; el borde, sin granizo. */
    private static void checkHailClouds() {
        long seed = io.github.hcuadrado428.stormroot.sky.CloudCover.SKY_SEED;
        WeatherFront hail = new WeatherFront(3, FrontType.THUNDERSTORM, 0, 0, 100, 0, 0, 500, 1000);
        WeatherFront plain = new WeatherFront(4, FrontType.THUNDERSTORM, 0, 0, 100, 0, 0, 500, 1000);
        io.github.hcuadrado428.stormroot.sky.CloudCover.Kind core =
                io.github.hcuadrado428.stormroot.sky.CloudCover.frontCloud(seed, 0, 0, 0, 0, 100, List.of(hail));
        io.github.hcuadrado428.stormroot.sky.CloudCover.Kind other =
                io.github.hcuadrado428.stormroot.sky.CloudCover.frontCloud(seed, 0, 0, 0, 0, 100, List.of(plain));
        io.github.hcuadrado428.stormroot.sky.CloudCover.Kind edge =
                io.github.hcuadrado428.stormroot.sky.CloudCover.frontCloud(seed, 80, 0, 80, 0, 100, List.of(hail));
        check("one in three storms brings hail",
                core == io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.HAIL
                        && other == io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.STORM
                        && edge != io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.HAIL,
                "núcleo de granizo=" + core + ", tormenta normal=" + other + ", borde=" + edge);
    }

    /** En la superficie del mundo plano: trigo maduro que retrocede, cristal que se rompe y granizo en el suelo. */
    private static void checkHailGround(ServerLevel level) {
        BlockPos cropAbove = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                new BlockPos(BX + 1, 0, BZ + 1));
        BlockPos glassAbove = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                new BlockPos(BX + 3, 0, BZ + 1));
        BlockState soil = level.getBlockState(cropAbove.below());
        BlockState glassGround = level.getBlockState(glassAbove.below());
        level.setBlock(cropAbove.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        net.minecraft.world.level.block.CropBlock wheat = (net.minecraft.world.level.block.CropBlock) Blocks.WHEAT;
        level.setBlock(cropAbove, wheat.getStateForAge(wheat.getMaxAge()), Block.UPDATE_CLIENTS);
        level.setBlock(glassAbove.below(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        net.minecraft.util.RandomSource random = level.getRandom();
        io.github.hcuadrado428.stormroot.weather.Hail.strike(level, cropAbove, random, 1.0, 1.0);
        io.github.hcuadrado428.stormroot.weather.Hail.strike(level, glassAbove, random, 1.0, 0.0);
        BlockState crop = level.getBlockState(cropAbove);
        int age = crop.getBlock() instanceof net.minecraft.world.level.block.CropBlock c ? c.getAge(crop) : -1;
        boolean glassBroken = !level.getBlockState(glassAbove.below()).is(Blocks.GLASS);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(cropAbove).inflate(2);
        List<net.minecraft.world.entity.item.ItemEntity> stones = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, box,
                e -> e.getItem().is(io.github.hcuadrado428.stormroot.registry.ModItems.HAILSTONE));
        int count = stones.size();
        for (net.minecraft.world.entity.item.ItemEntity stone : stones) {
            stone.discard();
        }
        level.setBlock(cropAbove, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(cropAbove.below(), soil, Block.UPDATE_CLIENTS);
        level.setBlock(glassAbove.below(), glassGround, Block.UPDATE_CLIENTS);
        check("hail hurts crops and glass and leaves hailstones",
                age == wheat.getMaxAge() - 2 && glassBroken && count == 1,
                "edad del trigo=" + age + " (máx " + wheat.getMaxAge() + "), cristal roto=" + glassBroken + ", granizo en el suelo=" + count);
    }

    /** Monzón: inunda la orilla de un agua natural (hasta 2 bloques), hace una cascada en la cueva y luego todo vuelve. */
    private static void monsoonFlood(ServerLevel level) {
        BlockPos above = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                new BlockPos(BX + 8, 0, BZ + 12));
        BlockPos lake = above.below();
        // Un lago de 1 bloque rodeado de hierba (la zona de pruebas es de piedra): se restaura al final.
        Map<BlockPos, BlockState> saved = new HashMap<>();
        List<BlockPos> banks = new ArrayList<>(List.of(lake, lake.east(2), lake.east(3)));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            banks.add(lake.relative(side));
        }
        for (BlockPos pos : banks) {
            saved.put(pos, level.getBlockState(pos));
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState ground = Blocks.GRASS_BLOCK.defaultBlockState();
        level.setBlock(lake, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        cell.set(0.95);
        int flooded = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (SeepageTicker.floodNext(level, cell, lake.relative(side))) {
                flooded++;
            }
        }
        // Segunda vuelta (2 bloques del agua) y tercera (3: no debe pasar).
        boolean second = SeepageTicker.floodNext(level, cell, lake.east(2));
        boolean third = SeepageTicker.floodNext(level, cell, lake.east(3));
        SeepageTicker.cascadeForTest(level, CX, CZ, cell);
        int cascades = cell.count(SeepageCell.Kind.CASCADE);
        BlockPos spout = null;
        for (SeepageCell.Change change : cell.changes()) {
            if (change.kind() == SeepageCell.Kind.CASCADE) {
                spout = BlockPos.of(change.pos());
            }
        }
        cell.setMonsoon(0);
        for (int i = 0; i < 2000 && (cell.count(SeepageCell.Kind.FLOOD) + cell.count(SeepageCell.Kind.CASCADE)) > 0; i++) {
            cell.set(0.3);
            SeepageTicker.applyEffectsForTest(level, CX, CZ, cell, 256);
        }
        boolean banksBack = true;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            banksBack &= level.getBlockState(lake.relative(side)).equals(ground);
        }
        banksBack &= level.getBlockState(lake.east(2)).equals(ground);
        boolean spoutGone = spout == null || level.getBlockState(spout).isAir();
        for (Map.Entry<BlockPos, BlockState> entry : saved.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        check("monsoon floods banks and caves and recedes",
                flooded == 4 && second && !third && cascades > 0 && banksBack && spoutGone,
                "orillas inundadas=" + flooded + ", a 2 bloques=" + second + ", a 3 bloques=" + third + ", cascadas=" + cascades
                        + ", orillas restauradas=" + banksBack + ", cascada quitada=" + spoutGone);
        cell.set(0);
    }

    /** Deshielo masivo: la helada se va en un minuto y deja el subsuelo empapado (inundación). */
    private static void checkMassiveThaw() {
        SeepageCell test = new SeepageCell();
        test.setFrost(1);
        for (int i = 0; i < 12; i++) {
            test.updateFrost(0, true, 5);
        }
        check("massive thaw floods the cave", test.frost() == 0 && test.deep() >= SeepageTicker.POOL_AT,
                "helada=" + test.frost() + ", humedad del subsuelo=" + test.deep());
        SeepageCell slow = new SeepageCell();
        slow.setFrost(1);
        slow.updateFrost(0, false, 600);
        check("slow thaw keeps the cave frozen", slow.frost() > 0.9, "helada tras 10 min sin ventisca=" + slow.frost());
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
