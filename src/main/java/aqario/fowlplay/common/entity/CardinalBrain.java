package aqario.fowlplay.common.entity;

import aqario.fowlplay.common.entity.ai.brain.FowlPlayActivities;
import aqario.fowlplay.common.entity.ai.brain.FowlPlayMemoryModuleType;
import aqario.fowlplay.common.entity.ai.brain.sensor.FowlPlaySensorType;
import aqario.fowlplay.common.entity.ai.brain.task.*;
import aqario.fowlplay.common.tags.FowlPlayItemTags;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.math.int_provider.UniformIntProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CardinalBrain {
    private static final ImmutableList<SensorType<? extends Sensor<? super CardinalEntity>>> SENSORS = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.NEAREST_ITEMS,
        SensorType.NEAREST_ADULT,
        SensorType.HURT_BY,
        SensorType.IS_IN_WATER,
        FowlPlaySensorType.IS_FLYING,
        FowlPlaySensorType.NEAREST_ADULTS
    );
    private static final ImmutableList<MemoryModuleType<?>> MEMORIES = ImmutableList.of(
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.MOBS,
        MemoryModuleType.VISIBLE_MOBS,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
//        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER,
        MemoryModuleType.AVOID_TARGET,
//        MemoryModuleType.ATTACK_COOLING_DOWN,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
//        MemoryModuleType.ATTACK_TARGET,
//        MemoryModuleType.HAS_HUNTING_COOLDOWN,
//        MemoryModuleType.TEMPTING_PLAYER,
//        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.GAZE_COOLDOWN_TICKS,
//        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
//        MemoryModuleType.NEAREST_ATTACKABLE,
        MemoryModuleType.IS_IN_WATER,
//        MemoryModuleType.IS_PREGNANT,
        MemoryModuleType.IS_PANICKING,
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
        MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM,
        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
        FowlPlayMemoryModuleType.IS_FLYING,
        FowlPlayMemoryModuleType.SEES_FOOD,
        FowlPlayMemoryModuleType.CANNOT_PICKUP_FOOD,
        FowlPlayMemoryModuleType.NEAREST_VISIBLE_ADULTS
    );
    private static final UniformIntProvider RUN_FROM_PLAYER_MEMORY_DURATION = TimeHelper.betweenSeconds(5, 7);
    private static final UniformIntProvider FOLLOW_ADULT_RANGE = UniformIntProvider.create(5, 16);
    private static final UniformIntProvider STAY_NEAR_ENTITY_RANGE = UniformIntProvider.create(16, 32);
    private static final int PICK_UP_RANGE = 32;
    private static final int AVOID_PLAYER_RADIUS = 10;
    private static final float RUN_SPEED = 1.4F;
    private static final float WALK_SPEED = 1.0F;
    private static final float FLY_SPEED = 2.0F;

    public static void init() {
    }

    public static Brain.Profile<CardinalEntity> createProfile() {
        return Brain.createProfile(MEMORIES, SENSORS);
    }

    public static Brain<?> create(Brain<CardinalEntity> brain) {
        addCoreActivities(brain);
        addIdleActivities(brain);
        addFlyActivities(brain);
        addAvoidActivities(brain);
        addPickupFoodActivities(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.resetPossibleActivities();
        return brain;
    }

    public static void reset(CardinalEntity cardinal) {
        cardinal.getBrain().resetPossibleActivities(
            ImmutableList.of(
                Activity.IDLE,
                FowlPlayActivities.FLY,
                Activity.AVOID,
                FowlPlayActivities.PICKUP_FOOD
            )
        );
    }

    private static void addCoreActivities(Brain<CardinalEntity> brain) {
        brain.setTaskList(
            Activity.CORE,
            0,
            ImmutableList.of(
                FlyingTaskControl.stopFalling(),
                new WalkTask<>(RUN_SPEED),
                makeAddPlayerToAvoidTargetTask(),
                LocateFoodTask.run(),
                new LookAroundTask(45, 90),
                new WanderAroundTask(),
                new ReduceCooldownTask(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
                new ReduceCooldownTask(MemoryModuleType.GAZE_COOLDOWN_TICKS)
            )
        );
    }

    private static void addIdleActivities(Brain<CardinalEntity> brain) {
        brain.setTaskList(
            Activity.IDLE,
            ImmutableList.of(
                Pair.of(1, new BreedTask(FowlPlayEntityType.CARDINAL, WALK_SPEED, 20)),
                Pair.of(2, WalkTowardClosestAdultTask.create(FOLLOW_ADULT_RANGE, WALK_SPEED)),
                Pair.of(3, FollowMobTask.create(CardinalBrain::isPlayerHoldingFood, 32.0F)),
                Pair.of(4, StayNearClosestEntityTask.create(STAY_NEAR_ENTITY_RANGE, WALK_SPEED)),
                Pair.of(5, new RandomLookAroundTask(
                    UniformIntProvider.create(150, 250),
                    30.0F,
                    0.0F,
                    0.0F
                )),
                Pair.of(
                    6,
                    new RandomTask<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT),
                        ImmutableList.of(
                            Pair.of(MeanderTask.create(WALK_SPEED), 4),
                            Pair.of(TaskBuilder.triggerIf(Entity::isInsideWaterOrBubbleColumn), 3),
                            Pair.of(new WaitTask(100, 300), 3),
                            Pair.of(FlyingTaskControl.startFlying(cardinal -> cardinal.getRandom().nextFloat() < 0.1F), 1)
                        )
                    )
                )
            ),
            ImmutableSet.of(
                Pair.of(FowlPlayMemoryModuleType.IS_FLYING, MemoryModuleState.VALUE_ABSENT),
                Pair.of(MemoryModuleType.AVOID_TARGET, MemoryModuleState.VALUE_ABSENT),
                Pair.of(FowlPlayMemoryModuleType.SEES_FOOD, MemoryModuleState.VALUE_ABSENT)
            )
        );
    }

    private static void addFlyActivities(Brain<CardinalEntity> brain) {
        brain.setTaskList(
            FowlPlayActivities.FLY,
            ImmutableList.of(
                Pair.of(1, FlyingTaskControl.stopFlying(cardinal -> true)),
                Pair.of(2, StayNearClosestEntityTask.create(STAY_NEAR_ENTITY_RANGE, FLY_SPEED)),
                Pair.of(
                    3,
                    new RandomTask<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT),
                        ImmutableList.of(
                            Pair.of(FlyTask.create(FLY_SPEED, 64, 32), 1)
                        )
                    )
                )
            ),
            ImmutableSet.of(
                Pair.of(FowlPlayMemoryModuleType.IS_FLYING, MemoryModuleState.VALUE_PRESENT),
                Pair.of(MemoryModuleType.AVOID_TARGET, MemoryModuleState.VALUE_ABSENT),
                Pair.of(FowlPlayMemoryModuleType.SEES_FOOD, MemoryModuleState.VALUE_ABSENT)
            )
        );
    }

    private static void addAvoidActivities(Brain<CardinalEntity> brain) {
        brain.setTaskList(
            Activity.AVOID,
            10,
            ImmutableList.of(
                FlyingTaskControl.startFlying(cardinal -> true),
                BetterGoToRememberedPositionTask.toEntity(
                    MemoryModuleType.AVOID_TARGET,
                    cardinal -> cardinal.isFlying() ? FLY_SPEED : RUN_SPEED,
                    AVOID_PLAYER_RADIUS,
                    true
                ),
                makeRandomFollowTask(),
                makeRandomWanderTask(),
                ForgetTask.run(entity -> true, MemoryModuleType.AVOID_TARGET)
            ),
            MemoryModuleType.AVOID_TARGET
        );
    }

    private static void addPickupFoodActivities(Brain<CardinalEntity> brain) {
        brain.setTaskList(
            FowlPlayActivities.PICKUP_FOOD,
            ImmutableList.of(
                Pair.of(0, BetterWalkToNearestWantedItemTask.create(
                    CardinalBrain::doesNotHaveFoodInHand,
                    entity -> entity.isFlying() ? FLY_SPEED : RUN_SPEED,
                    true,
                    PICK_UP_RANGE
                )),
                Pair.of(1, ForgetTask.run(CardinalBrain::noFoodInRange, FowlPlayMemoryModuleType.SEES_FOOD))
            ),
            Set.of(
                Pair.of(FowlPlayMemoryModuleType.SEES_FOOD, MemoryModuleState.VALUE_PRESENT),
                Pair.of(MemoryModuleType.AVOID_TARGET, MemoryModuleState.VALUE_ABSENT)
            )
        );
    }

    private static ImmutableList<Pair<ReportingTaskControl<LivingEntity>, Integer>> createLookTasks() {
        return ImmutableList.of(
            Pair.of(FollowMobTask.createMatchingType(FowlPlayEntityType.CARDINAL, 8.0F), 1),
            Pair.of(FollowMobTask.create(8.0F), 1)
        );
    }

    private static RandomTask<LivingEntity> makeRandomFollowTask() {
        return new RandomTask<>(
            ImmutableList.<Pair<? extends TaskControl<? super LivingEntity>, Integer>>builder()
                .addAll(createLookTasks())
                .add(Pair.of(new WaitTask(30, 60), 1))
                .build()
        );
    }

    private static RandomTask<CardinalEntity> makeRandomWanderTask() {
        return new RandomTask<>(
            ImmutableList.of(
                Pair.of(MeanderTask.create(0.6F), 2),
                Pair.of(TaskBuilder.sequence(
                    livingEntity -> true,
                    GoTowardsLookTarget.create(0.6F, 3)
                ), 2),
                Pair.of(new WaitTask(30, 60), 1)
            )
        );
    }

    private static TaskControl<CardinalEntity> makeAddPlayerToAvoidTargetTask() {
        return MemoryTransferTask.create(
            CardinalBrain::hasAvoidTarget, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.AVOID_TARGET, RUN_FROM_PLAYER_MEMORY_DURATION
        );
    }

    private static boolean hasAvoidTarget(CardinalEntity cardinal) {
        Brain<CardinalEntity> brain = cardinal.getBrain();
        if (!brain.hasMemoryModule(MemoryModuleType.NEAREST_VISIBLE_PLAYER)) {
            return false;
        }
        PlayerEntity player = brain.getOptionalMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).get();
        if (!EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(player)) {
            return false;
        }

        return cardinal.isInRange(player, AVOID_PLAYER_RADIUS);
    }

    public static void onAttacked(CardinalEntity cardinal, LivingEntity attacker) {
        if (attacker instanceof CardinalEntity) {
            return;
        }
        Brain<CardinalEntity> brain = cardinal.getBrain();
        brain.forget(FowlPlayMemoryModuleType.SEES_FOOD);
        if (attacker instanceof PlayerEntity) {
            brain.remember(FowlPlayMemoryModuleType.CANNOT_PICKUP_FOOD, true, 1200L);
        }
        brain.remember(MemoryModuleType.AVOID_TARGET, attacker, 160L);
        alertOthers(cardinal, attacker);
    }

    protected static void alertOthers(CardinalEntity cardinal, LivingEntity attacker) {
        getNearbyVisibleCardinals(cardinal).forEach(other -> {
            if (attacker instanceof PlayerEntity) {
                other.getBrain().remember(FowlPlayMemoryModuleType.CANNOT_PICKUP_FOOD, true, 1200L);
            }
            runAwayFrom((CardinalEntity) other, attacker);
        });
    }

    protected static void runAwayFrom(CardinalEntity cardinal, LivingEntity target) {
        cardinal.getBrain().forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        cardinal.getBrain().remember(MemoryModuleType.AVOID_TARGET, target, 160L);
    }

    protected static List<PassiveEntity> getNearbyVisibleCardinals(CardinalEntity cardinal) {
        return cardinal.getBrain().getOptionalMemory(FowlPlayMemoryModuleType.NEAREST_VISIBLE_ADULTS).orElse(ImmutableList.of());
    }

    private static boolean noFoodInRange(CardinalEntity cardinal) {
        Optional<ItemEntity> item = cardinal.getBrain().getOptionalMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        return item.isEmpty() || !item.get().isInRange(cardinal, PICK_UP_RANGE);
    }

    public static boolean isPlayerHoldingFood(LivingEntity target) {
        return target.getType() == EntityType.PLAYER && target.isHolding(stack -> getFood().test(stack));
    }

    private static boolean doesNotHaveFoodInHand(CardinalEntity cardinal) {
        return !getFood().test(cardinal.getMainHandStack());
    }

    public static Ingredient getFood() {
        return Ingredient.ofTag(FowlPlayItemTags.CARDINAL_FOOD);
    }
}