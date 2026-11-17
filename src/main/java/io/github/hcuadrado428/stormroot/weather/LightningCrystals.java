package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cuando cae un rayo sobre arena o piedra, a veces deja un Cristal de rayo.
 * Solo vanilla: cada loader llama a {@link #onEntityAdded} cuando una entidad entra en un mundo,
 * y {@link FrontTicker} llama a {@link #tick()} cada tick del servidor.
 *
 * <p>El cristal se suelta con retraso: si apareciera a la vez que el rayo, el propio rayo lo quemaría.
 */
public final class LightningCrystals {
    /** Ticks de espera antes de soltar el cristal (el rayo dura menos que esto). */
    public static final int DROP_DELAY_TICKS = 40;
    /** Límite de cristales pendientes, por si caen muchísimos rayos a la vez. */
    private static final int MAX_PENDING = 64;

    private static final List<Pending> PENDING = new ArrayList<>();

    private LightningCrystals() {
    }

    /**
     * Lo llama cada loader cuando una entidad entra en un mundo.
     *
     * @return {@code true} si el rayo debe CANCELARSE: es un rayo natural de la tormenta global de vanilla que cae
     *         fuera de cualquier frente de tormenta de Stormroot (así no caen rayos junto a jugadores que están
     *         fuera de la tormenta). Forge/NeoForge cancelan el evento; Fabric hace {@code discard()}.
     */
    public static boolean onEntityAdded(Entity entity, Level level) {
        if (!(entity instanceof LightningBolt bolt) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (isStrayStormLightning(bolt, serverLevel)) {
            return true;
        }
        BlockPos impact = entity.blockPosition();
        BlockState struck = serverLevel.getBlockState(impact.below());
        if (LightningRoots.canGrowInto(struck)
                && serverLevel.getRandom().nextDouble() < StormrootConfig.effectiveRootChance()) {
            LightningRoots.start(serverLevel, impact.below());
        }
        if (!struck.is(BlockTags.SAND) && !struck.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return false;
        }
        if (serverLevel.getRandom().nextDouble() >= StormrootConfig.lightningCrystalChance.getAsDouble() || PENDING.size() >= MAX_PENDING) {
            return false;
        }
        PENDING.add(new Pending(serverLevel, impact.immutable(), DROP_DELAY_TICKS));
        return false;
    }

    /**
     * Rayo natural (sin jugador que lo cause, p. ej. sin tridente) en el Overworld, mientras Stormroot pone el clima y
     * hay tormenta vanilla, en un punto donde ningún frente de tormenta llega al umbral.
     * Los rayos con el cielo despejado (/summon, otros mods) no se tocan.
     */
    public static boolean isStrayStormLightning(LightningBolt bolt, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || bolt.getCause() != null || !level.isThundering()
                || !FrontTicker.controlsWeatherNow()) {
            return false;
        }
        double x = bolt.getX();
        double z = bolt.getZ();
        for (WeatherFront front : FrontTicker.simulation(level.getServer()).fronts()) {
            if (front.type() == FrontType.THUNDERSTORM && front.intensityAt(x, z) >= FrontTicker.WEATHER_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (--pending.ticksLeft > 0) {
                continue;
            }
            it.remove();
            if (ModItems.LIGHTNING_CRYSTAL != null && pending.level.isLoaded(pending.pos)) {
                Block.popResource(pending.level, pending.pos, new ItemStack(ModItems.LIGHTNING_CRYSTAL));
            }
        }
    }

    /** Borra los pendientes (al cambiar de mundo, para no guardar referencias a mundos cerrados). */
    public static void clear() {
        PENDING.clear();
    }

    private static final class Pending {
        final ServerLevel level;
        final BlockPos pos;
        int ticksLeft;

        Pending(ServerLevel level, BlockPos pos, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }
}
