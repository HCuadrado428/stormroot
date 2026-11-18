package io.github.hcuadrado428.stormroot.entity;

import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import io.github.hcuadrado428.stormroot.mycel.MyceliumRules;
import io.github.hcuadrado428.stormroot.seep.SeepageCell;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Esporín: bichito pasivo de la red de micelio. Corretea por los hilos, huye de los jugadores y de la luz, se queda
 * dormido con la sequía y, cerca de un hilo, hace crecer la red de vez en cuando. Suelta esporas al morir.
 */
public class Sporeling extends PathfinderMob {
    /** Cada cuánto (ticks) un esporín despierto ayuda a crecer a la red. */
    public static final int FEED_INTERVAL = 200;
    private boolean dormant;

    public Sporeling(EntityType<? extends Sporeling> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.28);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DormantGoal());
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(3, new io.github.hcuadrado428.stormroot.entity.goal.FleeLightGoal(this, 1.3));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0, 1.4));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public boolean isDormant() {
        return dormant;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel server) || this.tickCount % 100 != 0) {
            return;
        }
        SeepageCell cell = SeepageSavedData.get(server.getServer()).field().get(this.getBlockX() >> 4, this.getBlockZ() >> 4);
        dormant = cell != null && cell.deep() < MyceliumRules.DRY_AT;
        if (!dormant && this.tickCount % FEED_INTERVAL == 0) {
            feedNetwork(server);
        }
    }

    /** Si hay un hilo o nodo al lado, la red crece un paso desde ahí. */
    public boolean feedNetwork(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(this.blockPosition().offset(-1, -1, -1), this.blockPosition().offset(1, 1, 1))) {
            var state = level.getBlockState(pos);
            if (Mycelium.isVein(state) || Mycelium.isNode(state)) {
                return Mycelium.spread(level, pos.immutable(), this.getRandom());
            }
        }
        return false;
    }

    public static boolean scaredOfLight(Level level, BlockPos pos) {
        return io.github.hcuadrado428.stormroot.entity.goal.FleeLightGoal.scaredOfLight(level, pos);
    }

    /** Con la sequía se queda quieto, como un cascarón. */
    private final class DormantGoal extends Goal {
        DormantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return dormant;
        }

        @Override
        public void start() {
            Sporeling.this.getNavigation().stop();
        }
    }
}
