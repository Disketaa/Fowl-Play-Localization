package aqario.fowlplay.common.entity;

import aqario.fowlplay.common.entity.ai.control.BirdFlightMoveControl;
import aqario.fowlplay.common.entity.ai.control.BirdMoveControl;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public abstract class BirdEntity extends AnimalEntity {
    private static final TrackedData<Boolean> FLYING = DataTracker.registerData(BirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final BirdNavigation flyNavigation;
    private final MobNavigation landNavigation;
    private final BirdFlightMoveControl flightMoveControl;
    private final MoveControl landMoveControl;
    public int airTicks = 0;
    public int timeFlying = 0;

    protected BirdEntity(EntityType<? extends BirdEntity> entityType, World world) {
        super(entityType, world);
        this.flyNavigation = new BirdNavigation(this, world);
        this.flyNavigation.setCanPathThroughDoors(false);
        this.flyNavigation.setCanEnterOpenDoors(true);
        this.flyNavigation.setCanSwim(false);

        this.landNavigation = new MobNavigation(this, world);

        this.flightMoveControl = new BirdFlightMoveControl(this, 20, true);
        this.landMoveControl = new BirdMoveControl(this);

    }

    public static DefaultAttributeContainer.Builder createBirdAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0f)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2f)
            .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.0f);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FLYING, false);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Flying", this.isFlying());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setFlying(nbt.getBoolean("Flying"));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isOnGround() || this.isTouchingWater()) {
            this.setFlying(false);
            this.airTicks = 0;
        }
        if (!this.isOnGround() && !this.isTouchingWater()) {
            this.airTicks++;
        }
        if (this.airTicks > 10 && this.getHealth() > 2.0F) {
            this.setFlying(true);
        }
        if (this.isFlying()) {
            this.timeFlying++;
            this.setNoGravity(true);
        }
        else {
            this.timeFlying = 0;
            this.setNoGravity(false);
        }
    }

    public void flap() {
        Vec3d vec3d = this.getVelocity();
        if (!this.onGround && vec3d.y < 0.0) {
            this.setUpwardSpeed(this.getMovementSpeed());
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return !this.isFlying() && super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    @Override
    protected int computeFallDamage(float fallDistance, float damageMultiplier) {
        return super.computeFallDamage(fallDistance, damageMultiplier) / 2;
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
        if (!this.isFlying()) {
            super.fall(heightDifference, onGround, landedState, landedPosition);
        }
    }

    public boolean isFlying() {
        return this.dataTracker.get(FLYING);
    }

    public void setFlying(boolean flying) {
        this.dataTracker.set(FLYING, flying);

        this.moveControl = flying ? this.flightMoveControl : this.landMoveControl;
        this.navigation = flying ? this.flyNavigation : this.landNavigation;
    }

    public boolean isTargetBlocked(Vec3d target) {
        Vec3d vec3d = new Vec3d(this.getX(), this.getEyeY(), this.getZ());

        return this.world.raycast(new RaycastContext(vec3d, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).getType() != HitResult.Type.MISS;
    }

    public Vec3d getBlockInViewAway(Vec3d fleePos, float radiusAdd) {
        final float radius = 3.15F * -3 - this.getRandom().nextInt(24) - radiusAdd;
        final float angle = getAngle();
        final double extraX = radius * Math.sin((float) (Math.PI + angle));
        final double extraZ = radius * Math.cos(angle);
        final BlockPos radialPos = new BlockPos(fleePos.getX() + extraX, 0, fleePos.getZ() + extraZ);
        final BlockPos ground = getGroundPos(radialPos);
        final int distFromGround = (int) this.getY() - ground.getY();

        final BlockPos newPos;
        if (distFromGround > 8) {
            final int flightHeight = 4 + this.getRandom().nextInt(10);
            newPos = ground.up(flightHeight);
        } else {
            newPos = ground.up(this.getRandom().nextInt(6) + 1);
        }

        if (!this.isTargetBlocked(Vec3d.ofCenter(newPos)) && this.squaredDistanceTo(Vec3d.ofCenter(newPos)) > 1) {
            return Vec3d.ofCenter(newPos);
        }
        return null;
    }

    private BlockPos getGroundPos(BlockPos in){
        BlockPos pos = new BlockPos(in.getX(), this.getY(), in.getZ());
        while (pos.getY() > -64 && !world.getBlockState(pos).getMaterial().blocksLight() && world.getFluidState(pos).isEmpty()) {
            pos = pos.down();
        }
        return pos;
    }

    public Vec3d getBlockGrounding(Vec3d fleePos) {
        final float radius = 3.15F * -3 - this.getRandom().nextInt(24);
        final float angle = getAngle();
        final double extraX = radius * Math.sin((float) (Math.PI + angle));
        final double extraZ = radius * Math.cos(angle);
        final BlockPos radialPos = new BlockPos(fleePos.getX() + extraX, getY(), fleePos.getZ() + extraZ);
        BlockPos ground = this.getGroundPos(radialPos);
        if (ground.getY() == -64) {
            return this.getPos();
        } else {
            ground = this.getBlockPos();
            while (ground.getY() > -64 && !world.getBlockState(ground).getMaterial().blocksLight()) {
                ground = ground.down();
            }
        }
        if (!this.isTargetBlocked(Vec3d.ofCenter(ground.up()))) {
            return Vec3d.ofCenter(ground);
        }
        return null;
    }

    private float getAngle() {
        final float neg = this.getRandom().nextBoolean() ? 1 : -1;
        final float renderYawOffset = this.bodyYaw;
        return (0.0174532925F * renderYawOffset) + 3.15F + (this.getRandom().nextFloat() * neg);
    }

    public boolean isOverWater() {
        BlockPos pos = this.getBlockPos();
        while (pos.getY() > -64 && world.isAir(pos)) {
            pos = pos.down();
        }
        return !world.getFluidState(pos).isEmpty();
    }
}
