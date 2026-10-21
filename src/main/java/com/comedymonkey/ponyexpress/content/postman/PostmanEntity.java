package com.comedymonkey.ponyexpress.content.postman;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Pony Express rider. This holds what he's doing (camping, rowing, carrying mail) and plays his animations;
 * the travelling and delivering is added on top of this.
 */
public class PostmanEntity extends PathfinderMob {
	/** Something he keeps doing until told otherwise; synced so everyone sees the same animation. */
	public enum Activity {
		NONE,
		CAMP_HAMMER,
		CAMP_REST,
		ROW
	}

	/** A one-off movement, sent to clients as an entity event. Lengths match the Blockbench animations. */
	public enum Gesture {
		PICKUP(100, 2.2F),
		DELIVER(101, 2.4F),
		WHISTLE(102, 1.6F),
		WAVE(103, 1.6F);

		public final byte eventId;
		public final float seconds;

		Gesture(int eventId, float seconds) {
			this.eventId = (byte) eventId;
			this.seconds = seconds;
		}

		@Nullable
		public static Gesture byEvent(byte id) {
			for (Gesture gesture : values())
				if (gesture.eventId == id)
					return gesture;
			return null;
		}
	}

	public static final float HURT_SECONDS = 0.4F;

	private static final EntityDataAccessor<Byte> DATA_ACTIVITY = SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_CARRYING = SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.BOOLEAN);

	// Client side only: the animations currently playing
	public final AnimationState idleAnimation = new AnimationState();
	public final AnimationState rideAnimation = new AnimationState();
	public final AnimationState rideIdleAnimation = new AnimationState();
	public final AnimationState hurtAnimation = new AnimationState();
	public final Map<Activity, AnimationState> activityAnimations = new EnumMap<>(Activity.class);
	public final Map<Gesture, AnimationState> gestureAnimations = new EnumMap<>(Gesture.class);

	public PostmanEntity(EntityType<? extends PostmanEntity> type, Level level) {
		super(type, level);
		for (Activity activity : Activity.values())
			activityAnimations.put(activity, new AnimationState());
		for (Gesture gesture : Gesture.values())
			gestureAnimations.put(gesture, new AnimationState());
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTIVITY, (byte) Activity.NONE.ordinal());
		builder.define(DATA_CARRYING, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	// ---------------------------------------------------------------- state

	public Activity getActivity() {
		int index = entityData.get(DATA_ACTIVITY);
		return index >= 0 && index < Activity.values().length ? Activity.values()[index] : Activity.NONE;
	}

	public void setActivity(Activity activity) {
		entityData.set(DATA_ACTIVITY, (byte) activity.ordinal());
	}

	/** Whether there's mail in his satchel, which makes it bulge. */
	public boolean isCarrying() {
		return entityData.get(DATA_CARRYING);
	}

	public void setCarrying(boolean carrying) {
		entityData.set(DATA_CARRYING, carrying);
	}

	/** Plays a one-off movement for everyone watching. */
	public void playGesture(Gesture gesture) {
		level().broadcastEntityEvent(this, gesture.eventId);
	}

	// ---------------------------------------------------------------- animations (client)

	@Override
	public void handleEntityEvent(byte id) {
		Gesture gesture = Gesture.byEvent(id);
		if (gesture == null) {
			super.handleEntityEvent(id);
			return;
		}
		gestureAnimations.values().forEach(AnimationState::stop);
		gestureAnimations.get(gesture).start(tickCount);
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		hurtAnimation.start(tickCount);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide)
			updateAnimations();
	}

	/** Whether a one-off gesture is still playing, which pauses the idle breathing. */
	public boolean isGesturing() {
		for (Gesture gesture : Gesture.values())
			if (gestureAnimations.get(gesture).isStarted())
				return true;
		return false;
	}

	private void updateAnimations() {
		for (Gesture gesture : Gesture.values()) {
			AnimationState state = gestureAnimations.get(gesture);
			if (state.isStarted() && state.getAccumulatedTime() > gesture.seconds * 1000)
				state.stop();
		}
		if (hurtAnimation.isStarted() && hurtAnimation.getAccumulatedTime() > HURT_SECONDS * 1000)
			hurtAnimation.stop();

		Activity activity = getActivity();
		for (Activity each : Activity.values())
			activityAnimations.get(each).animateWhen(each != Activity.NONE && each == activity, tickCount);

		Entity vehicle = getVehicle();
		boolean riding = vehicle != null && activity == Activity.NONE;
		boolean moving = vehicle != null && vehicle.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
		rideAnimation.animateWhen(riding && moving, tickCount);
		rideIdleAnimation.animateWhen(riding && !moving, tickCount);
		boolean walking = getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 && onGround();
		idleAnimation.animateWhen(vehicle == null && activity == Activity.NONE && !walking && !isGesturing(), tickCount);
	}

	// ---------------------------------------------------------------- saving

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("activity", getActivity().name());
		tag.putBoolean("carrying", isCarrying());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		try {
			setActivity(Activity.valueOf(tag.getString("activity")));
		} catch (IllegalArgumentException e) {
			setActivity(Activity.NONE);
		}
		setCarrying(tag.getBoolean("carrying"));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
