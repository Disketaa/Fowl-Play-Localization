package aqario.fowlplay.common.entity.ai.brain.task;

import aqario.fowlplay.common.entity.FlyingBirdEntity;
import aqario.fowlplay.common.entity.ai.brain.FowlPlayMemoryModuleType;
import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class FlockTask extends Task<FlyingBirdEntity> {
    public final float coherence;
    public final float alignment;
    public final float separation;
    public final float separationRange;
    private List<PassiveEntity> nearbyBirds;

    public FlockTask(float coherence, float alignment, float separation, float separationRange) {
        super(ImmutableMap.of());
        this.coherence = coherence;
        this.alignment = alignment;
        this.separation = separation;
        this.separationRange = separationRange;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, FlyingBirdEntity bird) {
        if (!bird.isFlying()) {
            return false;
        }
        if (!bird.getBrain().hasMemoryModule(FowlPlayMemoryModuleType.NEAREST_VISIBLE_ADULTS)) {
            return false;
        }
        this.nearbyBirds = bird.getBrain().getOptionalMemory(FowlPlayMemoryModuleType.NEAREST_VISIBLE_ADULTS).get();

        return !this.nearbyBirds.isEmpty();
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, FlyingBirdEntity bird, long time) {
        return this.shouldRun(world, bird);
    }

    @Override
    protected void keepRunning(ServerWorld world, FlyingBirdEntity bird, long time) {
//        bird.getNavigation().stop();
//        WalkTarget walkTarget = new WalkTarget(
//            new BlockPosLookTarget(this.alignment(bird)), 1, 1
//        );
//        bird.getBrain().remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(this.alignment(bird)));
//        bird.getBrain().remember(MemoryModuleType.WALK_TARGET, walkTarget);
        bird.addVelocity(this.random(bird));
        bird.addVelocity(this.cohesion(bird));
        bird.addVelocity(this.alignment(bird));
        bird.addVelocity(this.separation(bird));
    }

    public Vec3d random(FlyingBirdEntity bird) {
        Vec3d velocity = bird.getVelocity();

        if (MathHelper.abs((float) velocity.x) < 0.1 && MathHelper.abs((float) velocity.z) < 0.1) {
            return new Vec3d(this.randomSign(bird) * 0.4, this.randomSign(bird) * 0.4, this.randomSign(bird) * 0.4);
        }

        return Vec3d.ZERO;
    }

    public int randomSign(FlyingBirdEntity bird) {
        return bird.getRandom().nextBoolean() ? 1 : -1;
    }

    public Vec3d separation(FlyingBirdEntity bird) {
        Vec3d velocity = Vec3d.ZERO;

        for (PassiveEntity entity : this.nearbyBirds) {
            if ((entity.getPos().subtract(bird.getPos()).length()) < this.separationRange) {
                velocity = velocity.subtract(entity.getPos().subtract(bird.getPos()));
            }
        }

        return velocity.multiply(this.separation);
    }

    public Vec3d alignment(FlyingBirdEntity bird) {
        Vec3d velocity = Vec3d.ZERO;

        for (PassiveEntity entity : this.nearbyBirds) {
            velocity = velocity.add(entity.getVelocity());
        }

        velocity = velocity.multiply(1f / this.nearbyBirds.size());
        velocity = velocity.subtract(bird.getVelocity());
        return velocity.multiply(this.alignment);
    }

    public Vec3d cohesion(FlyingBirdEntity bird) {
        Vec3d velocity = Vec3d.ZERO;

        for (PassiveEntity entity : this.nearbyBirds) {
            velocity = velocity.add(entity.getPos());
        }

        velocity = velocity.multiply(1f / this.nearbyBirds.size());
        velocity = velocity.subtract(bird.getPos());
        return velocity.multiply(this.coherence);
    }
}