package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;

//? if >=1.21.2 {
import net.minecraft.world.entity.EntitySpawnReason;
//?}

/**
 * Tormenta seca: en los biomas sin lluvia (desierto, badlands, sabana) vanilla no echa rayos (sus rayos naturales
 * piden que llueva en ese bloque). Aquí Stormroot los echa él mismo, solo bajo el nubarrón de tormenta.
 *
 * <p>Los rayos hacen daño como los de vanilla y dejan cristales y raíces de rayo ({@link LightningCrystals}), pero NO
 * prenden fuego vanilla: {@code mixin/LightningBoltFireMixin} lo cancela. En su lugar, a veces prenden el fuego del clima
 * ({@code block/StormFireBlock}) en las hojas del árbol donde caen.
 */
public final class DryStorms {
    /** Cada cuántos ticks se prueba (por jugador). */
    private static final int INTERVAL = 20;
    /** Probabilidad por intento: con el jugador bajo una tormenta seca, ~1 rayo cada 10–15 s a su alrededor. */
    private static final double CHANCE = 0.12;
    /** Radio alrededor del jugador donde puede caer. */
    private static final int RANGE = 96;

    /** Rayos de tormenta seca vivos: hacen daño pero no prenden fuego ({@code mixin/LightningBoltFireMixin}). */
    private static final java.util.Map<LightningBolt, Boolean> OURS = new java.util.WeakHashMap<>();

    private static int ticks;
    private static long bolts;

    private DryStorms() {
    }

    /** Solo para el self-test: marca un rayo como de tormenta seca. */
    public static void markForTest(LightningBolt bolt) {
        OURS.put(bolt, Boolean.TRUE);
    }

    /** {@code true} si el rayo es de una tormenta seca (no debe prender fuego). */
    public static boolean isDryStormBolt(LightningBolt bolt) {
        return OURS.containsKey(bolt);
    }

    /** Rayos de tormenta seca lanzados desde que arrancó el juego (para el self-test). */
    public static long boltsSpawned() {
        return bolts;
    }

    /** Lo llama {@link FrontTicker} cada tick del servidor. */
    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        if (!FrontTicker.controlsWeatherNow()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (random.nextDouble() >= CHANCE) {
                continue;
            }
            int x = player.getBlockX() + random.nextInt(RANGE * 2 + 1) - RANGE;
            int z = player.getBlockZ() + random.nextInt(RANGE * 2 + 1) - RANGE;
            strikeIfDryStorm(level, x, z);
        }
    }

    /**
     * Lanza un rayo en (x, z) si ahí hay nubarrón de tormenta sobre un bioma sin lluvia y el chunk está cargado.
     *
     * @return {@code true} si cayó
     */
    public static boolean strikeIfDryStorm(ServerLevel level, int x, int z) {
        SkyWeather.Source sky = SkyWeather.serverSource();
        if (sky == null || SkyWeather.rainCloudAt(sky, x + 0.5, z + 0.5) != CloudCover.Kind.STORM) {
            return false;
        }
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) {
            return false;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        if (!BiomeClimate.isDry(level, top)) {
            return false;
        }
        //? if >=1.21.2 {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        //?} else {
        /*LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        *///?}
        if (bolt == null) {
            return false;
        }
        bolt.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
        OURS.put(bolt, Boolean.TRUE);
        level.addFreshEntity(bolt);
        bolts++;
        // Fuego del clima: a veces prende las hojas del árbol donde cae (nunca casas).
        if (level.getRandom().nextInt(2) == 0 && io.github.hcuadrado428.stormroot.block.StormFireBlock.fireAllowed(level, top)) {
            io.github.hcuadrado428.stormroot.block.StormFireBlock.igniteNear(level, top,
                    io.github.hcuadrado428.stormroot.registry.ModBlocks.STORM_FIRE);
        }
        return true;
    }
}
