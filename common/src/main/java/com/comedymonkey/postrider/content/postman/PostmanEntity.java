package com.comedymonkey.postrider.content.postman;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.content.lost.LostParcels;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The post rider. This holds what he's doing (camping, rowing, carrying mail) and plays his animations;
 * the travelling and delivering is added on top of this.
 */
public class PostmanEntity extends PathfinderMob {
	/** Something he keeps doing until told otherwise; synced so everyone sees the same animation. */
	public enum Activity {
		NONE,
		CAMP_HAMMER,
		CAMP_REST,
		ROW,
		HARMONICA,
		SLEEP,
		/** Working out his route, the map open in both hands (on foot or in the saddle). */
		STUDY_MAP,
		/** Up or down a ladder, hand over hand. */
		CLIMB,
		/** Carried up or down a bubble column. */
		DRIFT
	}

	/** A one-off movement, sent to clients as an entity event. Lengths match the Blockbench animations. */
	public enum Gesture {
		PICKUP(100, 2.2F),
		DELIVER(101, 2.4F),
		WHISTLE(102, 1.6F),
		WAVE(103, 1.6F),
		DRINK(104, 1.6F),
		LIGHT_FIRE(124, 3.0F),
		// Mannerisms on the road
		WIPE_BROW(105, 2.0F),
		TIP_HAT(106, 1.4F),
		PAT_HORSE(107, 2.0F),
		CHECK_SATCHEL(108, 1.8F),
		STRETCH(109, 2.4F),
		YAWN(110, 2.0F),
		LOOK_BACK(111, 2.2F),
		SCRATCH_HEAD(112, 2.0F),
		SWAT_FLY(113, 1.2F),
		ADJUST_HAT(114, 1.4F),
		PULL_HAT_DOWN(115, 1.4F),
		SHADE_EYES(116, 2.6F),
		SHIVER(117, 2.0F),
		SWIG(118, 2.2F),
		POCKET_WATCH(119, 2.4F),
		READ_LETTER(120, 2.8F),
		HUM(121, 3.0F),
		NECK_ROLL(122, 2.2F),
		CRACK_KNUCKLES(123, 1.6F),
		EAT(125, 2.6F);

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
	/** How often a rider checks the post office still has him down for his delivery. */
	public static final int WITHDRAWN_CHECK = 100;
	/** The camp_hammer animation brings the hammer down once a second. */
	public static final int HAMMER_BLOW_TICKS = 20;

	private static final EntityDataAccessor<Byte> DATA_ACTIVITY = SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_CARRYING = SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.BOOLEAN);
	/** Which of the crew this is (an id from {@link Roster}), so everyone sees their own face. */
	private static final EntityDataAccessor<String> DATA_RIDER = SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.STRING);

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
		avoidWater(this);
		// Up a stair or a slab at a step, not a hop (see RiderMoveControl)
		RiderMoveControl.Jump jump = new RiderMoveControl.Jump(this);
		jumpControl = jump;
		moveControl = new RiderMoveControl(this, jump);
	}

	/** He never wades or swims, even where vanilla pathfinding does his walking (to the mailbox's door, running away). */
	public static void avoidWater(PathfinderMob mob) {
		mob.setPathfindingMalus(PathType.WATER, -1);
		mob.setPathfindingMalus(PathType.WATER_BORDER, -1);
	}

	/** His movement speed attribute; sprinting adds 30% to it, as it does a player's. */
	public static final double BASE_SPEED = 0.3;
	public static final double SPRINT_BOOST = 1.3;
	/**
	 * How fast a mob settles to on ordinary ground, in blocks a second, for each unit of (speed squared): vanilla
	 * walking pushes it by its speed scaled by its speed again, against the ground's friction (0.6 x 0.91 a tick).
	 */
	public static final double PACE = 20 / (1 - 0.6 * 0.91);

	/** The move speed (the number pathfinding is given) that has a mob with this speed attribute cover so many blocks a second. */
	public static double moveSpeedFor(double blocksPerSecond, double attribute) {
		return Math.sqrt(blocksPerSecond / PACE) / attribute;
	}

	/** How many blocks a second a mob with this speed attribute covers at this move speed. */
	public static double blocksPerSecond(double moveSpeed, double attribute) {
		double speed = moveSpeed * attribute;
		return speed * speed * PACE;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTIVITY, (byte) Activity.NONE.ordinal());
		builder.define(DATA_CARRYING, false);
		builder.define(DATA_RIDER, "");
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new RiderNavigation(this, level);
	}

	// ---------------------------------------------------------------- state

	public Activity getActivity() {
		int index = entityData.get(DATA_ACTIVITY);
		return index >= 0 && index < Activity.values().length ? Activity.values()[index] : Activity.NONE;
	}

	public void setActivity(Activity activity) {
		entityData.set(DATA_ACTIVITY, (byte) activity.ordinal());
	}

	/** Which of the crew this is; null for a rider from before the roster (or a bad id), drawn in the plain uniform. */
	@Nullable
	public Roster.Rider getRider() {
		return Roster.byId(entityData.get(DATA_RIDER));
	}

	public void setRider(Roster.Rider rider) {
		entityData.set(DATA_RIDER, rider.id());
		setCustomName(Component.literal(rider.name()));
	}

	/** "his" or "her", for messages about this rider. */
	public String possessive() {
		Roster.Rider rider = getRider();
		return rider == null ? "his" : rider.possessive();
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
		this.gesture = gesture;
		gestureStartedAt = tickCount;
		gestureEndsAt = tickCount + Mth.ceil(gesture.seconds * 20);
	}

	/** When, in ticks from the start of the eat gesture, the bread reaches his mouth: a bite each time, with the sound and crumbs of it. */
	public static final int[] BITE_TICKS = {14, 31};

	/** Whether this many ticks into eating is one of the bites. */
	public static boolean isBite(int ticksIn) {
		for (int bite : BITE_TICKS)
			if (bite == ticksIn)
				return true;
		return false;
	}

	/** Server side: a bite of bread, heard and seen (crumbs) by everyone near. */
	private void bite(ServerLevel server) {
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5F, 0.9F + getRandom().nextFloat() * 0.2F);
		Vec3 look = getLookAngle();
		server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BREAD),
			getX() + look.x * 0.3, getEyeY() - 0.15, getZ() + look.z * 0.3, 5, 0.1, 0.05, 0.1, 0.02);
	}

	/** Server side: whether he's in the middle of a gesture or a drink, so nothing else should start. */
	public boolean isBusy() {
		return tickCount < gestureEndsAt || drinkTicks > 0;
	}

	public Mannerisms mannerisms() {
		return mannerisms;
	}

	// ---------------------------------------------------------------- arriving, leaving and the horse (server)

	/** How long after he starts whistling his horse appears, and how long after that he climbs on. */
	public static final int HORSE_ARRIVES_AFTER = 20;
	public static final int MOUNTS_AFTER = 10;

	private int horseCountdown;
	private int mountCountdown;
	@Nullable
	private PostmanHorse arrivingHorse;

	/** Brings one of the crew into the world in a puff of smoke. */
	@Nullable
	public static PostmanEntity appear(EntityType<PostmanEntity> type, ServerLevel level, double x, double y, double z, float yaw, Roster.Rider rider) {
		PostmanEntity postman = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (postman == null)
			return null;
		postman.snapTo(x, y, z, yaw, 0);
		postman.setYHeadRot(yaw);
		postman.setRider(rider);
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
	public static PostmanEntity setOut(ServerLevel level, PendingDelivery delivery, Roster.Rider who) {
		EntityType<PostmanEntity> type = ModEntities.POSTMAN.get();
		BlockPos mailbox = delivery.from().pos();
		level.getChunkAt(mailbox);
		Vec3 stand = DeliveryRun.standingSpot(level, mailbox, DeliveryRun.PICKUP_STAND_OFF);
		for (int up = 0; up <= 3 && !level.noCollision(type.getSpawnAABB(stand.x, stand.y, stand.z)); up++)
			stand = stand.add(0, 1, 0);
		if (!level.noCollision(type.getSpawnAABB(stand.x, stand.y, stand.z))) {
			// The stand in front of the box is in a bush, a bank, a tree: the nearest standing room will do
			stand = standingRoomNear(level, type, BlockPos.containing(DeliveryRun.standingSpot(level, mailbox, DeliveryRun.PICKUP_STAND_OFF)));
			if (stand == null)
				return null;
		}
		float yaw = DeliveryRun.yawToward(stand, Vec3.atBottomCenterOf(mailbox));
		PostmanEntity rider = appear(type, level, stand.x, stand.y, stand.z, yaw, who);
		if (rider != null)
			rider.run = DeliveryRun.plan(level, delivery);
		return rider;
	}

	/**
	 * Brings back a rider who vanished mid-delivery, still carrying the mail: where he was last seen if there's room to
	 * stand there, otherwise at the sender's mailbox. He whistles up his horse and carries on. Null if there's nowhere
	 * to put him just now.
	 */
	@Nullable
	public static PostmanEntity reappear(ServerLevel level, PendingDelivery delivery, Roster.Rider who, @Nullable BlockPos lastSeen) {
		EntityType<PostmanEntity> type = ModEntities.POSTMAN.get();
		Vec3 stand = lastSeen == null ? null : standingRoomNear(level, type, lastSeen);
		if (stand == null) {
			BlockPos mailbox = delivery.from().pos();
			level.getChunkAt(mailbox);
			stand = standingRoomNear(level, type, BlockPos.containing(DeliveryRun.standingSpot(level, mailbox, DeliveryRun.PICKUP_STAND_OFF)));
		}
		if (stand == null)
			return null;
		PostmanEntity rider = appear(type, level, stand.x, stand.y, stand.z, level.getRandom().nextFloat() * 360, who);
		if (rider != null) {
			rider.setCarrying(true);
			rider.run = DeliveryRun.resume(level, delivery);
		}
		return rider;
	}

	/** Somewhere near a spot (in the world, on solid ground, out of harm's way) with room for a rider to stand. */
	@Nullable
	private static Vec3 standingRoomNear(ServerLevel level, EntityType<PostmanEntity> type, BlockPos near) {
		if (level.isOutsideBuildHeight(near))
			return null;
		level.getChunkAt(near);
		BlockPos spot = LostParcels.findSpot(near, pos -> level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& !Hazards.isDangerous(level, pos) && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
		return spot == null ? null : Vec3.atBottomCenterOf(spot);
	}

	@Nullable
	public DeliveryRun getRun() {
		return run;
	}

	/** His horse is held to a walk: the way just ahead goes down, or there's fire or the like beside it (set each tick by the navigator). */
	private boolean heldToWalk;

	public boolean isHeldToWalk() {
		return heldToWalk;
	}

	public void setHeldToWalk(boolean heldToWalk) {
		this.heldToWalk = heldToWalk;
	}

	/** Whether his horse comes when he whistles: not once it's been killed, for the rest of that trip. */
	public boolean mayWhistle() {
		return run == null || !run.isHorseLost();
	}

	/** His horse was killed: he goes on without it for the rest of the trip (the next delivery, he has it again). */
	public void horseKilled(ServerLevel level, DamageSource source) {
		if (run != null)
			run.horseLost(this, level, source);
	}

	/**
	 * He whistles; a moment later his horse appears beside him in a puff of smoke and he climbs on. False if there's
	 * no whistling it up here: it's been killed this trip, or there's no room beside him for it (a steep hillside, a
	 * wood): then there's no sense waiting for it.
	 */
	public boolean whistleForHorse() {
		if (getVehicle() != null || horseCountdown > 0 || arrivingHorse != null)
			return true;
		if (!mayWhistle())
			return false;
		if (level() instanceof ServerLevel server) {
			EntityType<PostmanHorse> type = ModEntities.POSTMAN_HORSE.get();
			if (HorseSpots.find(position(), getYRot(), s -> HorseSpots.fits(server, type, s)) == null)
				return false;
		}
		playGesture(Gesture.WHISTLE);
		horseCountdown = HORSE_ARRIVES_AFTER;
		return true;
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

	/** Leaving the world for good (killed, vanished, removed) while asleep in a village bed, he leaves the bed free. */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && run != null && level() instanceof ServerLevel server)
			run.releaseBed(server);
		super.remove(reason);
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
	public double getVisibilityPercent(ServerLevel level, @Nullable Entity looker) {
		double visibility = super.getVisibilityPercent(level, looker);
		if (inTent && looker instanceof LivingEntity living && living.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE))
			visibility = Math.min(visibility, tentVisibility(living.getAttributeValue(Attributes.FOLLOW_RANGE)));
		return visibility;
	}

	/**
	 * Whether he's planted somewhere doing a job (camping, or at a mailbox) and can't be shoved off it by a passing
	 * sheep or player.
	 */
	public static boolean holdsStill(Activity activity, @Nullable DeliveryRun.Stage stage) {
		return activity != Activity.NONE || stage == DeliveryRun.Stage.PICKING_UP || stage == DeliveryRun.Stage.PLANNING
			|| stage == DeliveryRun.Stage.CALLING_HORSE || stage == DeliveryRun.Stage.DELIVERING;
	}

	@Override
	public boolean isPushable() {
		return super.isPushable() && !holdsStill(getActivity(), run == null ? null : run.stage());
	}

	// ---------------------------------------------------------------- health potions

	/** He sets out each day with this many potions of Instant Health II by default, restocked at 6am (see {@link PostRiderConfig}). */
	public static final int POTIONS_PER_DAY = 2;
	/** What a potion of Instant Health II heals: 4 hearts. */
	public static final float POTION_HEALS = 8.0F;
	/** A drink takes as long as a player drinking a potion, and as long as the drink animation. */
	public static final int DRINK_TICKS = 32;
	private static final ColorParticleOption HEALING_SWIRL = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.973F, 0.141F, 0.137F);

	private int potions = PostRiderConfig.potionsPerDay();
	/** The day his satchel was last restocked, or -1 before his first. */
	private long potionDay = -1;
	private int drinkTicks;
	private int gestureEndsAt;
	/** The gesture last played (server side), and when, for the bites of the eat gesture. */
	@Nullable
	private Gesture gesture;
	private int gestureStartedAt;
	private final Mannerisms mannerisms = new Mannerisms();

	public int getPotions() {
		return potions;
	}

	/** The Minecraft day a time of day falls in; each one starts at 6am. */
	public static long day(long dayTime) {
		return Math.floorDiv(dayTime, 24000L);
	}

	/** His potions after checking the day: a fresh pair on a new day, otherwise what he had. */
	public static int restocked(int potions, long lastDay, long today) {
		return today != lastDay ? PostRiderConfig.potionsPerDay() : potions;
	}

	/** Whether he reaches for a potion: below half health, not already drinking, and with one left. */
	public static boolean shouldDrink(float health, float maxHealth, int potions, boolean drinking) {
		return potions > 0 && !drinking && health > 0 && health < maxHealth / 2;
	}

	private void tickPotions(ServerLevel level) {
		long today = day(level.getOverworldClockTime());
		potions = restocked(potions, potionDay, today);
		potionDay = today;
		if (drinkTicks > 0) {
			drinkTicks--;
			if (drinkTicks % 8 == 4)
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6F, 0.9F + getRandom().nextFloat() * 0.2F);
			if (drinkTicks == 0) {
				heal(POTION_HEALS);
				level.sendParticles(HEALING_SWIRL, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.5, 0.3, 1.0);
			}
		} else if (shouldDrink(getHealth(), getMaxHealth(), potions, false)) {
			potions--;
			drinkTicks = DRINK_TICKS;
			playGesture(Gesture.DRINK);
		}
	}

	@Nullable
	public PostmanHorse getHorse() {
		return getVehicle() instanceof PostmanHorse horse ? horse : null;
	}

	/** His horse, in his livery, appearing here in a puff of smoke (not yet mounted). */
	public PostmanHorse summonHorse(ServerLevel level, Vec3 at) {
		PostmanHorse horse = PostmanHorse.create(ModEntities.POSTMAN_HORSE.get(), level, at.x, at.y, at.z, getYRot());
		horse.setRiderId(getUUID());
		Roster.Rider rider = getRider();
		if (rider != null)
			horse.dressAs(rider.horse());
		level.addFreshEntity(horse);
		PostmanEffects.puff(level, horse);
		return horse;
	}

	/** Whether he has whistled and his horse hasn't come yet. */
	public boolean isHorseOnTheWay() {
		return horseCountdown > 0 || arrivingHorse != null;
	}

	private void tickHorse(ServerLevel level) {
		if (horseCountdown > 0 && --horseCountdown == 0) {
			EntityType<PostmanHorse> type = ModEntities.POSTMAN_HORSE.get();
			Vec3 spot = HorseSpots.find(position(), getYRot(), s -> HorseSpots.fits(level, type, s));
			PostmanHorse horse = spot == null ? null : summonHorse(level, spot);
			if (horse != null) {
				arrivingHorse = horse;
				mountCountdown = MOUNTS_AFTER;
			}
		}
		if (arrivingHorse != null && --mountCountdown <= 0) {
			if (arrivingHorse.isAlive() && getVehicle() == null)
				startRiding(arrivingHorse, true, true);
			arrivingHorse = null;
		}
	}

	@Override
	public void die(DamageSource source) {
		if (run != null && !run.isOver() && level() instanceof ServerLevel server)
			run.killed(this, server, source);
		// His horse goes with him, even one still on its way; a horse that has lost its rider has no one to carry
		if (getVehicle() instanceof PostmanHorse horse) {
			stopRiding();
			vanish(horse);
		}
		if (arrivingHorse != null) {
			vanish(arrivingHorse);
			arrivingHorse = null;
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
	public boolean hurtServer(ServerLevel server, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(server, source, amount);
		if (hurt && run != null)
			run.hurt(this, server, source, amount);
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
		if (level().isClientSide())
			updateAnimations();
		else if (level() instanceof ServerLevel server) {
			tickHorse(server);
			// A horse from before horses knew their riders (an old save) learns whose it is, so its death is noticed
			if (getVehicle() instanceof PostmanHorse horse && horse.riderId() == null)
				horse.setRiderId(getUUID());
			// Carried under a low roof on horseback he'd suffocate: he gets down, and plans on from there on foot
			if (getVehicle() instanceof PostmanHorse && isInWall()) {
				PostRider.LOGGER.info("Rider {} gets down from his horse at {} {} {}: no room for him on its back there", getName().getString(), getBlockX(), getBlockY(), getBlockZ());
				dismissHorse();
			}
			if (isAlive())
				tickPotions(server);
			if (tickCount % 5 == 0)
				RiderHunting.startleCreepers(this, server);
			if (getActivity() == Activity.CAMP_HAMMER && tickCount % HAMMER_BLOW_TICKS == 0)
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8F, 0.9F + getRandom().nextFloat() * 0.2F);
			if (gesture == Gesture.EAT && isBite(tickCount - gestureStartedAt))
				bite(server);
			if (run != null && tickCount % WITHDRAWN_CHECK == 0 && !PostOffice.get(server.getServer()).isCarrying(run.delivery().id(), getUUID())) {
				// Gone missing a while (and his mail sent with someone else), or left over from a muddle: he leaves quietly
				run.withdraw(this, server);
				run = null;
				vanish();
				return;
			}
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
			if (state.isStarted() && state.getTimeInMillis(tickCount) > gesture.seconds * 1000)
				state.stop();
		}
		if (hurtAnimation.isStarted() && hurtAnimation.getTimeInMillis(tickCount) > HURT_SECONDS * 1000)
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
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("activity", getActivity().name());
		tag.putBoolean("carrying", isCarrying());
		tag.putBoolean("in_tent", inTent);
		tag.putString("rider", entityData.get(DATA_RIDER));
		tag.putInt("potions", potions);
		tag.putLong("potion_day", potionDay);
		tag.putInt("drink_ticks", drinkTicks);
		if (run != null)
			tag.store("run", CompoundTag.CODEC, run.save(registryAccess()));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		try {
			setActivity(Activity.valueOf(tag.getStringOr("activity", "")));
		} catch (IllegalArgumentException e) {
			setActivity(Activity.NONE);
		}
		setCarrying(tag.getBooleanOr("carrying", false));
		inTent = tag.getBooleanOr("in_tent", false);
		entityData.set(DATA_RIDER, tag.getStringOr("rider", ""));
		if (tag.getInt("potions").isPresent()) {
			potions = tag.getIntOr("potions", 0);
			potionDay = tag.getLongOr("potion_day", 0);
			drinkTicks = tag.getIntOr("drink_ticks", 0);
		}
		run = tag.read("run", CompoundTag.CODEC).map(run -> DeliveryRun.load(run, registryAccess())).orElse(null);
	}

	/** Riders keep to their own dimension: one carried off through a portal would be lost to the post office. */
	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
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
