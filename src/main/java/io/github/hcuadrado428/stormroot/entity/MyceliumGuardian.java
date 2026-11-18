package io.github.hcuadrado428.stormroot.entity;

import io.github.hcuadrado428.stormroot.entity.goal.FleeLightGoal;
import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Guardián de micelio: un gigante lento de raíces y setas (30 corazones) que duerme sobre un nodo de las redes grandes.
 * Despierta si le atacas o si rompes nodos cerca, y entonces llama a los tejedores.
 * <ul>
 *   <li>Golpe fuerte cuerpo a cuerpo.</li>
 *   <li><b>Nube de esporas</b> a su alrededor: náusea y lentitud.</li>
 *   <li><b>Raíces</b>: el objetivo queda clavado al suelo un momento (lentitud muy fuerte).</li>
 *   <li>Débil a la luz (con antorchas cerca se debilita) y al fuego (ardiendo no usa esporas ni raíces).</li>
 * </ul>
 * Al morir, su red se calma: deja de crecer y sus tejedores ya no atacan. Suelta el corazón de micelio.
 */
public class MyceliumGuardian extends Monster {
    public static final int CLOUD_COOLDOWN = 160;
    public static final int ROOTS_COOLDOWN = 200;
    private boolean awake;
    private int cloudCooldown = 60;
    private int rootsCooldown = 100;

    public MyceliumGuardian(EntityType<? extends MyceliumGuardian> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.ARMOR, 4.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SleepGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public boolean isAwake() {
        return awake;
    }

    /** Despierta (si duerme), va a por {@code target} y llama a los tejedores de alrededor. */
    public void wake(LivingEntity target) {
        awake = true;
        if (target != null) {
            this.setTarget(target);
            if (this.level() instanceof ServerLevel server) {
                for (Weaver weaver : server.getEntities(ModEntities.weaver(), this.getBoundingBox().inflate(16), e -> true)) {
                    weaver.setTarget(target);
                }
            }
        }
    }

    public static boolean weakenedByLight(Level level, net.minecraft.core.BlockPos pos) {
        return FleeLightGoal.scaredOfLight(level, pos);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (!awake && this.getLastHurtByMob() != null) {
            wake(this.getLastHurtByMob());
        }
        if (!awake) {
            return;
        }
        if (this.tickCount % 20 == 0 && weakenedByLight(server, this.blockPosition())) {
            // La luz lo debilita: pega menos y va más lento.
            this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
        }
        LivingEntity target = this.getTarget();
        if (target == null || this.isOnFire()) {
            return;
        }
        double dist = this.distanceToSqr(target);
        if (--cloudCooldown <= 0 && dist < 36) {
            cloudCooldown = CLOUD_COOLDOWN;
            sporeCloud(server);
        }
        if (--rootsCooldown <= 0 && dist < 100) {
            rootsCooldown = ROOTS_COOLDOWN;
            roots(server, target);
        }
    }

    /** Nube de esporas a su alrededor: náusea y lentitud a quien la pise. */
    public AreaEffectCloud sporeCloud(ServerLevel level) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, this.getX(), this.getY(), this.getZ());
        cloud.setOwner(this);
        cloud.setRadius(3.5F);
        cloud.setRadiusPerTick(-0.01F);
        cloud.setDuration(120);
        cloud.setWaitTime(0);
        //? if >=1.21.11 {
        cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
        //?} else {
        /*cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
        *///?}
        level.addFreshEntity(cloud);
        return cloud;
    }

    /** Raíces que salen del suelo: el objetivo queda casi inmóvil un segundo y medio. */
    public static void roots(ServerLevel level, LivingEntity target) {
        //? if >=1.21.11 {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5));
        //?} else {
        /*target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5));
        *///?}
        level.sendParticles(ParticleTypes.MYCELIUM, target.getX(), target.getY() + 0.2, target.getZ(), 30, 0.6, 0.3, 0.6, 0.02);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel server) {
            Mycelium.onGuardianDefeated(server, this.blockPosition());
        }
    }

    /** Dormido: no se mueve ni mira (hasta que algo lo despierta). */
    private final class SleepGoal extends Goal {
        SleepGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !awake;
        }

        @Override
        public void start() {
            MyceliumGuardian.this.getNavigation().stop();
        }
    }
}
