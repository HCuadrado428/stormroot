package io.github.hcuadrado428.stormroot.entity;

import io.github.hcuadrado428.stormroot.entity.goal.FleeLightGoal;
import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Tejedor: habitante neutral de la red de micelio, como una araña blanda de hongo. Repara la red (vuelve a tejer hilos
 * donde faltan, a oscuras) y no ataca si no le provocas: si le pegas o rompes un nodo cerca, te enreda con hilos
 * pegajosos (lentitud, casi sin daño). Huye de la luz igual que la red. Suelta hilo de micelio.
 */
public class Weaver extends PathfinderMob {
    /** Segundos de lentitud que deja cada golpe. */
    public static final int ENTANGLE_TICKS = 60;
    private int repairCooldown;
    /** ¿Vive en una red domesticada? (se mira cada 5 s). */
    private boolean tamed;

    public boolean isTamed() {
        return tamed;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.tickCount % 100 == 0 && this.level() instanceof ServerLevel server) {
            tamed = Mycelium.networks(server).stateAt(Mycelium.chunkKey(this.blockPosition()))
                    == io.github.hcuadrado428.stormroot.mycel.MyceliumNetworks.State.TAMED;
        }
    }

    public Weaver(EntityType<? extends Weaver> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeLightGoal(this, 1.25));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.addGoal(3, new RepairGoal());
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        // En una red domesticada, defiende la zona de los monstruos.
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this,
                net.minecraft.world.entity.monster.Monster.class, true) {
            @Override
            public boolean canUse() {
                return tamed && super.canUse();
            }
        });
    }

    /** Hilos pegajosos: cada golpe deja lentitud. */
    public static void entangle(Entity target) {
        if (target instanceof LivingEntity living) {
            //? if >=1.21.11 {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ENTANGLE_TICKS, 1));
            //?} else {
            /*living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ENTANGLE_TICKS, 1));
            *///?}
        }
    }

    //? if >=1.21.2 {
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            entangle(target);
        }
        return hit;
    }
    //?} else {
    /*@Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            entangle(target);
        }
        return hit;
    }
    *///?}

    /** Vuelve a tejer la red: un paso de crecimiento desde un hilo o nodo de al lado (a oscuras). */
    public boolean repairNear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(this.blockPosition().offset(-2, -1, -2), this.blockPosition().offset(2, 2, 2))) {
            var state = level.getBlockState(pos);
            if ((Mycelium.isVein(state) || Mycelium.isNode(state)) && Mycelium.spread(level, pos.immutable(), this.getRandom())) {
                return true;
            }
        }
        return false;
    }

    /** Cada pocos segundos, si no está peleando, teje un poco la red que tiene alrededor. */
    private final class RepairGoal extends Goal {
        RepairGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Weaver self = Weaver.this;
            if (self.getTarget() != null || --repairCooldown > 0) {
                return false;
            }
            repairCooldown = 80 + self.getRandom().nextInt(80);
            return self.level() instanceof ServerLevel && !FleeLightGoal.scaredOfLight(self.level(), self.blockPosition());
        }

        @Override
        public void start() {
            repairNear((ServerLevel) Weaver.this.level());
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
