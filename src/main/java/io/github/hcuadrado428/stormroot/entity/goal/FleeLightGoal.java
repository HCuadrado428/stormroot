package io.github.hcuadrado428.stormroot.entity.goal;

import io.github.hcuadrado428.stormroot.mycel.MyceliumRules;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/** Los habitantes de la red huyen de la luz de las antorchas: buscan un sitio oscuro cerca. */
public class FleeLightGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private Vec3 target;

    public FleeLightGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public static boolean scaredOfLight(Level level, BlockPos pos) {
        return MyceliumRules.scaredBy(level.getBrightness(LightLayer.BLOCK, pos));
    }

    @Override
    public boolean canUse() {
        if (!scaredOfLight(mob.level(), mob.blockPosition())) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            Vec3 pos = DefaultRandomPos.getPos(mob, 10, 4);
            if (pos != null && !scaredOfLight(mob.level(), BlockPos.containing(pos))) {
                target = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }
}
