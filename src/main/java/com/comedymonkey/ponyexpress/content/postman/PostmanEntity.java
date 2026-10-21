package com.comedymonkey.ponyexpress.content.postman;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.post.PendingDelivery;
import com.comedymonkey.ponyexpress.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
		ROW,
		HARMONICA
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
	/** The camp_hammer animation brings the hammer down once a second. */
	public static final int HAMMER_BLOW_TICKS = 20;

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

	// ---------------------------------------------------------------- arriving, leaving and the horse (server)

	/** How long after he starts whistling his horse appears, and how long after that he climbs on. */
	public static final int HORSE_ARRIVES_AFTER = 20;
	public static final int MOUNTS_AFTER = 10;

	private int horseCountdown;
	private int mountCountdown;
	@Nullable
	private PostmanHorse arrivingHorse;

	/** Brings a new rider into the world in a puff of smoke, with a name of his own. */
	@Nullable
	public static PostmanEntity appear(EntityType<PostmanEntity> type, ServerLevel level, double x, double y, double z, float yaw) {
		PostmanEntity postman = type.create(level);
		if (postman == null)
			return null;
		postman.moveTo(x, y, z, yaw, 0);
		postman.setYHeadRot(yaw);
		postman.setCustomName(Component.literal(PostmanNames.rider(level.random)));
		level.addFreshEntity(postman);
		PostmanEffects.puff(level, postman);
		return postman;
	}

	@Nullable
	private DeliveryRun run;

	/**
	 * Sends a rider out with a delivery: he appears in front of the sender's mailbox and the run begins. Null if
	 * there's nowhere for him to stand.
	 */
	@Nullable
	public static PostmanEntity setOut(ServerLevel level, PendingDelivery delivery) {
		EntityType<PostmanEntity> type = ModEntities.POSTMAN.get();
		BlockPos mailbox = delivery.from().pos();
		level.getChunkAt(mailbox);
		Vec3 stand = DeliveryRun.standingSpot(level, mailbox, DeliveryRun.PICKUP_STAND_OFF);
		for (int up = 0; up <= 3 && !level.noCollision(type.getSpawnAABB(stand.x, stand.y, stand.z)); up++)
			stand = stand.add(0, 1, 0);
		if (!level.noCollision(type.getSpawnAABB(stand.x, stand.y, stand.z)))
			return null;
		float yaw = DeliveryRun.yawToward(stand, Vec3.atBottomCenterOf(mailbox));
		PostmanEntity rider = appear(type, level, stand.x, stand.y, stand.z, yaw);
		if (rider != null)
			rider.run = DeliveryRun.plan(level, delivery);
		return rider;
	}

	@Nullable
	public DeliveryRun getRun() {
		return run;
	}

	/** He whistles; a moment later his horse appears beside him in a puff of smoke and he climbs on. */
	public void whistleForHorse() {
		if (getVehicle() != null || horseCountdown > 0 || arrivingHorse != null)
			return;
		playGesture(Gesture.WHISTLE);
		horseCountdown = HORSE_ARRIVES_AFTER;
	}

	/** He gets down and his horse vanishes in a puff of smoke. */
	public void dismissHorse() {
		if (getVehicle() instanceof PostmanHorse horse) {
			stopRiding();
			vanish(horse);
		}
		if (arrivingHorse != null) {
			vanish(arrivingHorse);
			arrivingHorse = null;
		}
		horseCountdown = 0;
	}

	/** He and his horse vanish in a puff of smoke. */
	public void vanish() {
		dismissHorse();
		vanish(this);
	}

	private void vanish(Entity entity) {
		if (level() instanceof ServerLevel server)
			PostmanEffects.puff(server, entity);
		entity.discard();
	}

	// ---------------------------------------------------------------- the tent

	/** Inside his tent, mobs only notice him this close. */
	public static final double TENT_NOTICE_RANGE = 4.0;
	private boolean inTent;

	public boolean isInTent() {
		return inTent;
	}

	public void setInTent(boolean inTent) {
		this.inTent = inTent;
	}

	/** How much of their usual sight range a mob needs to notice him: all of it, or just 4 blocks' worth in his tent. */
	public static double tentVisibility(double lookerRange) {
		return lookerRange <= TENT_NOTICE_RANGE ? 1.0 : TENT_NOTICE_RANGE / lookerRange;
	}

	@Override
	public double getVisibilityPercent(@Nullable Entity looker) {
		double visibility = super.getVisibilityPercent(looker);
		if (inTent && looker instanceof LivingEntity living && living.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE))
			visibility = Math.min(visibility, tentVisibility(living.getAttributeValue(Attributes.FOLLOW_RANGE)));
		return visibility;
	}

	@Nullable
	public PostmanHorse getHorse() {
		return getVehicle() instanceof PostmanHorse horse ? horse : null;
	}

	private void tickHorse(ServerLevel level) {
		if (horseCountdown > 0 && --horseCountdown == 0) {
			EntityType<PostmanHorse> type = ModEntities.POSTMAN_HORSE.get();
			Vec3 spot = HorseSpots.find(position(), getYRot(), s -> HorseSpots.fits(level, type, s));
			PostmanHorse horse = spot == null ? null : PostmanHorse.create(type, level, spot.x, spot.y, spot.z, getYRot());
			if (horse != null) {
				level.addFreshEntity(horse);
				PostmanEffects.puff(level, horse);
				arrivingHorse = horse;
				mountCountdown = MOUNTS_AFTER;
			}
		}
		if (arrivingHorse != null && --mountCountdown <= 0) {
			if (arrivingHorse.isAlive())
				startRiding(arrivingHorse, true);
			arrivingHorse = null;
		}
	}

	@Override
	public void die(DamageSource source) {
		if (run != null && !run.isOver() && level() instanceof ServerLevel server)
			run.killed(this, server, source);
		// His horse goes with him; a horse that has lost its rider has no one to carry
		if (getVehicle() instanceof PostmanHorse horse) {
			stopRiding();
			vanish(horse);
		}
		super.die(source);
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
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && run != null && source.getEntity() instanceof LivingEntity attacker && level() instanceof ServerLevel server)
			run.attacked(this, server, attacker);
		return hurt;
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
		else if (level() instanceof ServerLevel server) {
			tickHorse(server);
			if (tickCount % 5 == 0)
				RiderHunting.startleCreepers(this, server);
			if (getActivity() == Activity.CAMP_HAMMER && tickCount % HAMMER_BLOW_TICKS == 0)
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8F, 0.9F + getRandom().nextFloat() * 0.2F);
			if (run != null && !isRemoved()) {
				run.tick(this, server);
				if (run.isOver())
					run = null;
			}
		}
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
		tag.putBoolean("in_tent", inTent);
		if (run != null)
			tag.put("run", run.save(registryAccess()));
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
		inTent = tag.getBoolean("in_tent");
		run = tag.contains("run") ? DeliveryRun.load(tag.getCompound("run"), registryAccess()) : null;
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
