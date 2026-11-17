package io.github.hcuadrado428.stormroot.mycel;

import io.github.hcuadrado428.stormroot.seep.SeepageTicker;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bruma de esporas: la niebla sobre pantanos, manglares y campos de champiñones lleva esporas.
 * <ul>
 *   <li>Respirarla mucho rato ({@link MyceliumRules#NAUSEA_AFTER} s) da náusea leve. Protege una calabaza tallada en la
 *       cabeza o subir a terreno alto (más de {@link MyceliumRules#MIST_CEILING} bloques sobre el mar).</li>
 *   <li>Las setas de alrededor se extienden más rápido (ticks aleatorios de más).</li>
 *   <li>La red de micelio crece el doble (eso lo hace {@link MyceliumRules#growthChance} con la celda con niebla).</li>
 * </ul>
 * Las partículas las pone el cliente ({@code ClientWeather}).
 */
public final class SporeMist {
    /** Intensidad de niebla a partir de la cual hay bruma de esporas. */
    public static final double MIST_AT = 0.3;
    private static final int INTERVAL = 20;
    private static final int MUSHROOM_RANGE = 12;
    private static final int MUSHROOM_TRIES = 16;

    private static final Map<UUID, Integer> EXPOSURE = new HashMap<>();
    private static int ticks;

    private SporeMist() {
    }

    public static void tick(ServerLevel level) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        FrontSimulation sim = FrontTicker.simulationIfRunning();
        if (sim == null || !FrontTicker.controlsWeatherNow()) {
            EXPOSURE.clear();
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            boolean mist = SeepageTicker.fogAt(sim, player.getX(), player.getZ()) >= MIST_AT
                    && HostBiomes.isHostColumn(level, player.getBlockX(), player.getBlockZ());
            if (mist) {
                spreadMushrooms(level, player.blockPosition(), random);
            }
            breathe(player, mist && !protectedFrom(level, player));
        }
    }

    /** Un segundo respirando (o no) la bruma; con mucha exposición, náusea. Devuelve si se ha mareado ahora. */
    public static boolean breathe(ServerPlayer player, boolean inMist) {
        int exposure = MyceliumRules.expose(EXPOSURE.getOrDefault(player.getUUID(), 0), inMist);
        boolean sick = MyceliumRules.nauseous(exposure);
        if (sick) {
            //? if >=1.21.11 {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, MyceliumRules.NAUSEA_SECONDS * 20, 0));
            //?} else {
            /*player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MyceliumRules.NAUSEA_SECONDS * 20, 0));
            *///?}
            exposure -= MyceliumRules.NAUSEA_RELIEF;
        }
        EXPOSURE.put(player.getUUID(), exposure);
        return sick;
    }

    public static boolean protectedFrom(ServerLevel level, ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)
                || player.getY() > level.getSeaLevel() + MyceliumRules.MIST_CEILING;
    }

    /** Ticks aleatorios de más a las setas de alrededor (se extienden como en vanilla, pero más deprisa). */
    public static int spreadMushrooms(ServerLevel level, BlockPos center, RandomSource random) {
        int boosted = 0;
        for (int i = 0; i < MUSHROOM_TRIES; i++) {
            BlockPos pos = center.offset(random.nextInt(MUSHROOM_RANGE * 2 + 1) - MUSHROOM_RANGE, random.nextInt(9) - 4,
                    random.nextInt(MUSHROOM_RANGE * 2 + 1) - MUSHROOM_RANGE);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MushroomBlock) {
                state.randomTick(level, pos, random);
                boosted++;
            }
        }
        return boosted;
    }

    public static void reset(ServerPlayer player) {
        EXPOSURE.remove(player.getUUID());
    }
}
