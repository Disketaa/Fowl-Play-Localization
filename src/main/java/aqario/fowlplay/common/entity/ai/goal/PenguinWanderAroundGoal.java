package aqario.fowlplay.common.entity.ai.goal;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;

public class PenguinWanderAroundGoal extends WanderAroundFarGoal {
    public PenguinWanderAroundGoal(PathAwareEntity pathAwareEntity, double d) {
        super(pathAwareEntity, d, 1);
    }

    @Override
    protected Vec3d getWanderTarget() {
        return NoPenaltyTargeting.find(this.mob, 5, 7);
    }
}