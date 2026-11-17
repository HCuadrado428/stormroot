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

    public static void onEntityAdded(Entity entity, Level level) {
        if (!(entity instanceof LightningBolt) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos impact = entity.blockPosition();
        BlockState struck = serverLevel.getBlockState(impact.below());
        if (!struck.is(BlockTags.SAND) && !struck.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return;
        }
        if (serverLevel.getRandom().nextDouble() >= StormrootConfig.lightningCrystalChance.getAsDouble() || PENDING.size() >= MAX_PENDING) {
            return;
        }
        PENDING.add(new Pending(serverLevel, impact.immutable(), DROP_DELAY_TICKS));
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
