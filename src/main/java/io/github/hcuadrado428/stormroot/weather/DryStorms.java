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
 * <p>Los rayos son "solo visuales" para vanilla: no prenden fuego (el fuego del clima, solo en hojas, llegará aparte)
 * ni hacen daño, pero sí dejan cristales de rayo y raíces de rayo ({@link LightningCrystals}).
 */
public final class DryStorms {
    /** Cada cuántos ticks se prueba (por jugador). */
    private static final int INTERVAL = 20;
    /** Probabilidad por intento: con el jugador bajo una tormenta seca, ~1 rayo cada 10–15 s a su alrededor. */
    private static final double CHANCE = 0.12;
    /** Radio alrededor del jugador donde puede caer. */
    private static final int RANGE = 96;

    private static int ticks;
    private static long bolts;

    private DryStorms() {
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
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        bolts++;
        return true;
    }
}
