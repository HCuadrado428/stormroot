package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Raíces de rayo: desde el bloque donde cae un rayo crece hacia abajo una columna de cristal, un bloque cada pocos
 * ticks, hasta llegar a una cueva (o a {@link #MAX_DEPTH} bloques). Seguirla lleva bajo tierra.
 * Solo atraviesa piedra, tierra, arena y grava. Lo arranca {@link LightningCrystals}; {@link FrontTicker} llama a
 * {@link #tick()} cada tick.
 */
public final class LightningRoots {
    public static final int MAX_DEPTH = 40;
    private static final int GROW_EVERY_TICKS = 3;
    private static final int MAX_ACTIVE = 16;

    private static final List<Growth> ACTIVE = new ArrayList<>();

    private LightningRoots() {
    }

    public static boolean canGrowInto(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
    }

    public static void start(ServerLevel level, BlockPos top) {
        if (ACTIVE.size() < MAX_ACTIVE && ModBlocks.LIGHTNING_ROOT != null) {
            ACTIVE.add(new Growth(level, top.immutable()));
        }
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Growth> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Growth growth = it.next();
            if (--growth.cooldown > 0) {
                continue;
            }
            growth.cooldown = GROW_EVERY_TICKS;
            if (!growth.grow()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static final class Growth {
        final ServerLevel level;
        BlockPos cursor;
        int remaining = MAX_DEPTH;
        int cooldown = GROW_EVERY_TICKS;

        Growth(ServerLevel level, BlockPos top) {
            this.level = level;
            this.cursor = top;
        }

        /** @return {@code false} cuando ha terminado */
        boolean grow() {
            if (remaining <= 0 || !level.isLoaded(cursor)) {
                return false;
            }
            BlockState state = level.getBlockState(cursor);
            if (!canGrowInto(state)) {
                return false; // aire (cueva alcanzada), agua, minerales, bloques de jugadores...
            }
            level.setBlock(cursor, ModBlocks.LIGHTNING_ROOT.defaultBlockState(), Block.UPDATE_ALL);
            cursor = cursor.below();
            remaining--;
            return true;
        }
    }
}
