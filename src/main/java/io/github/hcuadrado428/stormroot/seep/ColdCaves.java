package io.github.hcuadrado428.stormroot.seep;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

//? if >=1.21.2 {
import net.minecraft.world.entity.EntitySpawnReason;
//?} else {
/*import net.minecraft.world.entity.MobSpawnType;
*///?}

/**
 * Peligros de las cuevas heladas (helada del chunk ≥ {@link SeepageTicker#ICICLE_AT}):
 * <ul>
 *   <li><b>Congelación</b>: bajo tierra, el jugador se va congelando como en la nieve polvo de vanilla (se ralentiza y,
 *       del todo congelado, recibe daño). La ropa de cuero protege (vanilla: {@code canFreeze()}).</li>
 *   <li><b>Strays</b>: a oscuras aparecen strays de vez en cuando (como mucho {@link #MAX_STRAYS} cerca).</li>
 * </ul>
 */
public final class ColdCaves {
    private static final int INTERVAL = 20;
    /** Por segundo, vanilla quita 40 de congelación fuera de la nieve polvo: lo que se suma de más es lo que sube. */
    private static final int VANILLA_THAW_PER_SECOND = 40;
    private static final double STRAY_CHANCE = 0.02;
    private static final int MAX_STRAYS = 3;

    private static int ticks;
    private static long strays;

    private ColdCaves() {
    }

    /** Strays que han aparecido en cuevas heladas (para el self-test). */
    public static long straysSpawned() {
        return strays;
    }

    public static void tick(MinecraftServer server) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        if (!StormrootConfig.seepageActive()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) {
            return;
        }
        SeepageField field = SeepageSavedData.get(server).field();
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            SeepageCell cell = field.get(pos.getX() >> 4, pos.getZ() >> 4);
            if (cell == null || cell.frost() < SeepageTicker.ICICLE_AT || level.canSeeSky(pos) || player.isSpectator()) {
                continue;
            }
            chill(player, cell.frost());
            maybeSpawnStray(level, player, level.getRandom());
        }
    }

    /** Suma congelación: más cuanto más helada está la cueva (de ~1 min a ~25 s hasta congelarse del todo). */
    public static void chill(ServerPlayer player, double frost) {
        if (!player.canFreeze() || player.isCreative()) {
            return;
        }
        int gain = VANILLA_THAW_PER_SECOND + (int) Math.round(2 + 4 * frost);
        int max = player.getTicksRequiredToFreeze() + 40;
        player.setTicksFrozen(Math.min(max, player.getTicksFrozen() + gain));
    }

    private static void maybeSpawnStray(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextDouble() >= STRAY_CHANCE) {
            return;
        }
        AABB around = player.getBoundingBox().inflate(24);
        if (level.getEntities(EntityType.STRAY, around, entity -> true).size() >= MAX_STRAYS) {
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos at = player.blockPosition().offset(random.nextInt(17) - 8, random.nextInt(5) - 2, random.nextInt(17) - 8);
            if (at.distSqr(player.blockPosition()) < 36 || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
                    || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
                    || level.getBrightness(LightLayer.BLOCK, at) > 0 || level.canSeeSky(at)) {
                continue;
            }
            //? if >=1.21.2 {
            Entity stray = EntityType.STRAY.spawn(level, at, EntitySpawnReason.NATURAL);
            //?} else {
            /*Entity stray = EntityType.STRAY.spawn(level, at, MobSpawnType.NATURAL);
            *///?}
            if (stray != null) {
                strays++;
            }
            return;
        }
    }
}
