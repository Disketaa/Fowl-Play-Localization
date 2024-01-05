package aqario.fowlplay.common.entity;

import aqario.fowlplay.common.entity.ai.goal.BirdFollowOwnerGoal;
import aqario.fowlplay.common.entity.ai.goal.DeliverBundleGoal;
import aqario.fowlplay.common.sound.FowlPlaySoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.random.RandomGenerator;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.core.manager.SingletonAnimationFactory;

import java.util.Optional;
import java.util.UUID;

public class PigeonEntity extends TameableBirdEntity implements IAnimatable {
    private final AnimationFactory factory = new SingletonAnimationFactory(this);
    private static final TrackedData<Optional<UUID>> RECIPIENT = DataTracker.registerData(PigeonEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    public float flapProgress;
    public float maxWingDeviation;
    public float prevMaxWingDeviation;
    public float prevFlapProgress;
    public float flapSpeed = 1.0f;
    private int eatingTime;

    public PigeonEntity(EntityType<? extends PigeonEntity> entityType, World world) {
        super(entityType, world);
//        this.moveControl = new FlightMoveControl(this, 90, false);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0f);
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, -1.0f);
        this.setPathfindingPenalty(PathNodeType.DANGER_POWDER_SNOW, -1.0f);
        this.setPathfindingPenalty(PathNodeType.COCOA, -1.0f);
        this.setPathfindingPenalty(PathNodeType.FENCE, -1.0f);
    }

    @Override
    protected void initEquipment(RandomGenerator random, LocalDifficulty difficulty) {
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0f);
        this.setEquipmentDropChance(EquipmentSlot.OFFHAND, 1.0f);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(RECIPIENT, Optional.empty());
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.getRecipientUuid() != null) {
            nbt.putUuid("Recipient", this.getRecipientUuid());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (!nbt.containsUuid("Receiver")) {
            this.setRecipientUuid(null);
            return;
        }
        this.setRecipientUuid(nbt.getUuid("Receiver"));
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
            .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.0f)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2f);
    }

    protected void initGoals() {
        this.goalSelector.add(0, new EscapeDangerGoal(this, 1.8));
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new DeliverBundleGoal<>(this, 1.0, 6.0F, 128.0F, false));
        this.goalSelector.add(3, new BirdFollowOwnerGoal(this, 1.0, 10.0F, 2.0F, false));
        this.goalSelector.add(4, new FleeEntityGoal<>(this, PlayerEntity.class, entity -> !this.isTamed(), 6.0f, 1.4, 1.8, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR::test));
        this.goalSelector.add(5, new AnimalMateGoal(this, 1.0));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 20.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

//    @Override
//    protected EntityNavigation createNavigation(World world) {
//        BirdNavigation birdNavigation = new BirdNavigation(this, world);
//        birdNavigation.setCanPathThroughDoors(false);
//        birdNavigation.setCanSwim(true);
//        birdNavigation.setCanEnterOpenDoors(true);
//        return birdNavigation;
//    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 0.45f;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (this.getStackInHand(Hand.OFF_HAND).isEmpty() && stack.getItem() instanceof BundleItem && stack.hasCustomName() && this.isTamed()) {
            if (!this.world.isClient) {
                this.setStackInHand(Hand.OFF_HAND, stack);
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
            return ActionResult.success(this.world.isClient);
        }

        if (stack.isEmpty() && this.getStackInHand(Hand.OFF_HAND).getItem() instanceof BundleItem) {
            if (!this.world.isClient) {
                player.setStackInHand(hand, this.getStackInHand(Hand.OFF_HAND));
                this.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            }
            return ActionResult.success(this.world.isClient);
        }

        if (!stack.isEmpty() && !this.isBreedingItem(stack)) {
            if (!this.world.isClient) {
                this.setStackInHand(Hand.MAIN_HAND, stack);
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
            return ActionResult.success(this.world.isClient);
        }

        if (!this.isBreedingItem(stack)) {
            return super.interactMob(player, hand);
        }

        if (!this.isTamed()) {
            if (!this.world.isClient) {
                this.eat(player, hand, stack);
                if (this.random.nextInt(4) == 0) {
                    this.setOwner(player);
//                        this.setSitting(true);
                    this.navigation.stop();
                    this.world.sendEntityStatus(this, (byte) 7);
                } else {
                    this.world.sendEntityStatus(this, (byte) 6);
                }
            }
            return ActionResult.success(this.world.isClient);
        }

        return super.interactMob(player, hand);
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return !this.isTamed() && stack.isOf(Items.WHEAT_SEEDS);
    }

    @Override
    public boolean canEquip(ItemStack stack) {
        EquipmentSlot equipmentSlot = MobEntity.getPreferredEquipmentSlot(stack);
        if (!this.getEquippedStack(equipmentSlot).isEmpty()) {
            return false;
        }
        return equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND && super.canEquip(stack);
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();

        this.dropStack(this.getEquippedStack(EquipmentSlot.MAINHAND));
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.dropStack(this.getEquippedStack(EquipmentSlot.OFFHAND));
        this.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    @Override
    public SoundEvent getEatSound(ItemStack stack) {
        return SoundEvents.ENTITY_FOX_EAT;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.getServer() == null) {
            return;
        }

        ItemStack stack = this.getEquippedStack(EquipmentSlot.MAINHAND);
        ServerPlayerEntity recipient = this.getServer().getPlayerManager().getPlayer(stack.getName().getString());

        if (!(stack.getItem() instanceof BundleItem) || !stack.hasCustomName() || recipient == null || recipient.getUuid() == null) {
            this.setRecipientUuid(null);
            return;
        }

        this.setRecipientUuid(recipient.getUuid());
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
//        this.flapWings();
    }

    private void flapWings() {
        this.prevFlapProgress = this.flapProgress;
        this.prevMaxWingDeviation = this.maxWingDeviation;
        this.maxWingDeviation += (float) (this.onGround || this.hasVehicle() ? -1 : 4) * 0.3f;
        this.maxWingDeviation = MathHelper.clamp(this.maxWingDeviation, 0.0f, 1.0f);
        if (!this.onGround && this.flapSpeed < 1.0f) {
            this.flapSpeed = 1.0f;
        }
        this.flapSpeed *= 0.9f;
        Vec3d vec3d = this.getVelocity();
        if (!this.onGround && vec3d.y < 0.0) {
            this.setVelocity(vec3d.multiply(1.0, 0.6, 1.0));
        }
        this.flapProgress += this.flapSpeed * 2.0f;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
    }

    @Override
    protected void addFlapEffects() {
        this.playSound(SoundEvents.ENTITY_PARROT_FLY, 0.15f, 1.0f);
    }

    public UUID getRecipientUuid() {
        return this.dataTracker.get(RECIPIENT).orElse(null);
    }

    public void setRecipientUuid(@Nullable UUID uuid) {
        this.dataTracker.set(RECIPIENT, Optional.ofNullable(uuid));
    }

    @Override
    public Vec3d getLeashOffset() {
        return new Vec3d(0.0, 0.5f * this.getStandingEyeHeight(), this.getWidth() * 0.4f);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(10) == 0 ? FowlPlaySoundEvents.ENTITY_PIGEON_CALL : FowlPlaySoundEvents.ENTITY_PIGEON_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    private PlayState predicate(AnimationEvent<PigeonEntity> event) {
//        if (this.isFlying()) {
//            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.seagull.flying", ILoopType.EDefaultLoopTypes.LOOP));
//            return PlayState.CONTINUE;
//        }
//        if (this.isTouchingWater()) {
//            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.seagull.idle", ILoopType.EDefaultLoopTypes.LOOP));
//            return PlayState.CONTINUE;
//        }
        if (event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.pigeon.walk", ILoopType.EDefaultLoopTypes.LOOP));
            return PlayState.CONTINUE;
        }
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.pigeon.idle", ILoopType.EDefaultLoopTypes.LOOP));
        return PlayState.CONTINUE;
    }

    public boolean isMoving(AnimationEvent<PigeonEntity> event) {
        float limbSwingAmount = event.getLimbSwingAmount();
        return Math.abs(limbSwingAmount) >= 0.05F;
    }

    @Override
    public void registerControllers(AnimationData animationData) {
        animationData.addAnimationController(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}