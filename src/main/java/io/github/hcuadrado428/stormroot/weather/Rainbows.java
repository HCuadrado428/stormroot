package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.sky.Rainbow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Arcoíris (evento raro): cuando se acaba una lluvia, tormenta o monzón cerca de un jugador, de día, a veces sale un
 * arcoíris. Uno de sus extremos toca el suelo cerca del jugador: quien llegue allí antes de que se borre se lleva un
 * fragmento de arcoíris y unas esmeraldas. No se guarda con el mundo (dura 2,5 minutos).
 */
public final class Rainbows {
    /** Probabilidad de arcoíris al acabarse una lluvia cerca de un jugador. */
    public static final double CHANCE = 0.35;
    /** Distancia del extremo al jugador. */
    private static final int MIN_DISTANCE = 40;
    private static final int MAX_DISTANCE = 90;
    /** Hay que llegar a menos de esto del extremo. */
    private static final double REACH = 4;
    private static final int MAX_ACTIVE = 4;

    private static final List<Rainbow> ACTIVE = new ArrayList<>();
    private static long nextId = 1;

    private Rainbows() {
    }

    public static List<Rainbow> active() {
        return Collections.unmodifiableList(ACTIVE);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /** Se acabó un frente que trae lluvia: quizá un arcoíris cerca de algún jugador. */
    public static void onRainEnded(ServerLevel level, WeatherFront front) {
        RandomSource random = level.getRandom();
        long time = level.getDayTime() % 24000L;
        if (time < 1000 || time > 11000 || ACTIVE.size() >= MAX_ACTIVE || random.nextDouble() >= CHANCE) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - front.x();
            double dz = player.getZ() - front.z();
            double reach = front.radius() + 256;
            if (dx * dx + dz * dz <= reach * reach) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
                spawn(level, player.getX() + Math.cos(angle) * distance, player.getZ() + Math.sin(angle) * distance,
                        random.nextDouble() * Math.PI * 2);
                return;
            }
        }
    }

    /** Pone un arcoíris con su extremo en (x, z) (en el suelo). */
    public static Rainbow spawn(ServerLevel level, double x, double z, double angle) {
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
        return spawn(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, angle);
    }

    /** Pone un arcoíris con su extremo exactamente en (x, y, z). */
    public static Rainbow spawn(ServerLevel level, double x, double y, double z, double angle) {
        long now = level.getGameTime();
        Rainbow rainbow = new Rainbow(nextId++, x, y, z, angle, now, now + Rainbow.DURATION_TICKS);
        ACTIVE.add(rainbow);
        FrontTicker.sync(level.getServer());
        return rainbow;
    }

    /** Cada tick: quita los que se han acabado y da la recompensa a quien llegue al extremo. */
    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean changed = ACTIVE.removeIf(r -> now >= r.endTick());
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Rainbow rainbow = ACTIVE.get(i);
            for (ServerPlayer player : level.players()) {
                double dx = player.getX() - rainbow.x();
                double dz = player.getZ() - rainbow.z();
                if (dx * dx + dz * dz <= REACH * REACH && Math.abs(player.getY() - rainbow.y()) < 6) {
                    reward(level, BlockPos.containing(rainbow.x(), rainbow.y(), rainbow.z()));
                    ACTIVE.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            FrontTicker.sync(level.getServer());
        }
    }

    /** El tesoro del final del arcoíris: un fragmento de arcoíris y de 1 a 3 esmeraldas. */
    public static void reward(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        drop(level, pos, new ItemStack(ModItems.RAINBOW_SHARD));
        drop(level, pos, new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
    }

    private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }
}
