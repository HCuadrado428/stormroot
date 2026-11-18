package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * La aurora calma la noche: bajo ella, al aire libre, salen muchos menos mobs hostiles. Cada loader llama a
 * {@link #onEntityAdded} junto a {@code LightningCrystals.onEntityAdded}; {@code true} = no dejar que aparezca.
 * No toca mobs con nombre ni los que no pueden desaparecer (los de los jugadores).
 */
public final class AuroraCalm {
    /** Con la aurora a pleno, de cada 10 hostiles que intentan aparecer al aire libre, no salen 8. */
    public static final double CALM = 0.8;
    public static final double MIN_INTENSITY = 0.3;

    private AuroraCalm() {
    }

    public static boolean onEntityAdded(Entity entity, Level level) {
        if (!(entity instanceof Enemy) || !(entity instanceof Mob mob) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (mob.isPersistenceRequired() || mob.hasCustomName() || serverLevel.dimension() != Level.OVERWORLD) {
            return false;
        }
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null || !FrontTicker.controlsWeatherNow() || !serverLevel.canSeeSky(mob.blockPosition())) {
            return false;
        }
        double aurora = SeepageTicker.intensityAt(sim, FrontType.AURORA, mob.getX(), mob.getZ());
        return blocks(RareSkies.isNight(serverLevel), aurora, serverLevel.getRandom().nextDouble());
    }

    /** Regla pura: de noche y bajo una aurora de al menos {@link #MIN_INTENSITY}, se impide con probabilidad CALM × intensidad. */
    public static boolean blocks(boolean night, double aurora, double roll) {
        return night && aurora >= MIN_INTENSITY && roll < CALM * Math.min(1, aurora);
    }
}
