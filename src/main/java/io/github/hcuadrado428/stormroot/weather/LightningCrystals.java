package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cristal de rayo: cuando un rayo (natural, de una tormenta de Stormroot o de /summon) cae sobre arena
 * o piedra, con probabilidad {@link StormrootConfig#lightningCrystalChance} suelta un
 * {@code lightning_crystal} en el punto del impacto.
 *
 * <p>Solo vanilla. Cada loader llama a {@link #onEntityJoin} cuando una entidad entra en un nivel
 * (Fabric: {@code ServerEntityEvents.ENTITY_LOAD}; Forge/NeoForge: {@code EntityJoinLevelEvent})
 * y a {@link #onServerTick} al final de cada tick.
 *
 * <p>El cristal no se suelta en el mismo momento: se apunta y se suelta
 * {@link StormrootConfig#lightningCrystalDelayTicks} ticks después, cuando el rayo ya no daña
 * (además así no se añaden entidades mientras el mundo está añadiendo otra).
 */
public final class LightningCrystals {
    private static final List<Strike> PENDING = new ArrayList<>();

    private LightningCrystals() {
    }

    private record Strike(ServerLevel level, BlockPos pos, int dueTick) {
    }

    public static void onEntityJoin(Entity entity, Level level) {
        if (!(entity instanceof LightningBolt) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Mismo cálculo que LightningBolt#getStrikePosition (privado): el bloque justo debajo del rayo.
        BlockPos struck = BlockPos.containing(entity.getX(), entity.getY() - 1.0E-6, entity.getZ());
        int due = serverLevel.getServer().getTickCount() + StormrootConfig.lightningCrystalDelayTicks;
        PENDING.add(new Strike(serverLevel, struck, due));
    }

    public static void onServerTick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Strike> ready = new ArrayList<>();
        Iterator<Strike> it = PENDING.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (strike.level().getServer() != server) {
                it.remove(); // de un mundo que ya se cerró
            } else if (now >= strike.dueTick()) {
                it.remove();
                ready.add(strike);
            }
        }
        for (Strike strike : ready) {
            tryDropCrystal(strike.level(), strike.pos());
        }
    }

    private static void tryDropCrystal(ServerLevel level, BlockPos struck) {
        BlockState state = level.getBlockState(struck);
        if (!state.is(BlockTags.SAND) && !state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return;
        }
        if (level.getRandom().nextDouble() >= StormrootConfig.lightningCrystalChance) {
            return;
        }
        Block.popResource(level, struck.above(), new ItemStack(ModItems.LIGHTNING_CRYSTAL));
    }
}
