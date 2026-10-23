package com.comedymonkey.postrider.content.postman;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRiderConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ferry barge a rider conjures to cross water he can't ride round. He sits on the bench at the bow rowing, his
 * horse stands on the deck behind him, and it goes where the delivery run steers it at a boat's speed. It can't be
 * hurt, and vanishes in a puff of smoke if its rider is gone.
 */
public class BargeEntity extends Entity {
	/** A boat's top speed on water, and the barge's unless the server says otherwise (see {@link PostRiderConfig}). */
	public static final double BLOCKS_PER_SECOND = 8.0;
	public static final double SPEED = BLOCKS_PER_SECOND / 20;

	/** Its top speed in blocks per tick on this server. */
	public static double speed() {
		return PostRiderConfig.bargeBlocksPerSecond() / 20;
	}
	/** How fast it comes up to speed, and how far it turns each tick. */
	private static final double ACCELERATION = 0.02;
	public static final float TURN_PER_TICK = 4.0F;
	/**
	 * How far the hull's sides reach below the deck, as drawn in blockbench/barge.bbmodel. The barge's position is
	 * the bottom of the hull, so its hit box runs from there up past the railings and bumps into a bank at the
	 * waterline rather than sliding over it.
	 */
	public static final double HULL_DEPTH = 4 / 16.0;
	/** The railings stand this high above the deck; the hit box reaches their tops. */
	public static final double RAILING_HEIGHT = 14 / 16.0;
	/**
	 * How high the deck floats above the water: only the bottom pixel of the hull is in it, so it rides high and
	 * floats in water a single block deep.
	 */
	public static final double FREEBOARD = 3 / 16.0;

	/** Where the barge (the bottom of its hull) floats on water whose surface is at this height. */
	public static double floatHeight(double surface) {
		return surface + FREEBOARD - HULL_DEPTH;
	}
	/** Where the bench and deck are from the middle of the deck (blocks forward, and up), as in blockbench/barge.bbmodel. */
	public static final double BENCH_FORWARD = 21 / 16.0, BENCH_HEIGHT = 7 / 16.0;
	public static final double DECK_FORWARD = -5 / 16.0, DECK_HEIGHT = 2 / 16.0;
	/** How far the bow reaches ahead of the middle. */
	public static final double BOW_REACH = 2.5;
	/** Its box is a touch narrower than the 3-block hull: in a lead just wide enough, a box the full width caught an edge at every bend. */
	public static final double BOX_WIDTH = 2.75;
	/** It stops with its bow this far short of anything that isn't open water (a bank, a wall, a low roof): enough that the rider's seat, in the bow, stays clear. */
	public static final double STOPS_SHORT = 0.5;
	/** Nothing but open water ahead of the bow this tick; false when it's up against something (land, if he's lucky). */
	private boolean blockedAhead;
	/** The rider sits with his hips this high above his feet (the row animation's seated pose). */
	public static final double HIP_HEIGHT = 12 / 16.0;
	/** One stroke of the oars, as long as the row animation. */
	public static final int STROKE_TICKS = 24;
	private static final int CHECK_INTERVAL = 20;

	private static final EntityDataAccessor<Boolean> DATA_ROWING = SynchedEntityData.defineId(BargeEntity.class, EntityDataSerializers.BOOLEAN);

	// Client side only: the animations playing
	public final AnimationState rowAnimation = new AnimationState();
	public final AnimationState idleAnimation = new AnimationState();

	/** Server side: where it's being steered, or null to drift to a stop. */
	@Nullable
	private Vec3 heading;
	private double speed;
	private int lerpSteps;
	private double lerpX, lerpY, lerpZ, lerpYRot;

	public BargeEntity(EntityType<? extends BargeEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ROWING, false);
	}

	public boolean isRowing() {
		return entityData.get(DATA_ROWING);
	}

	/** Steers towards a spot; null to stop rowing. */
	public void steer(@Nullable Vec3 heading) {
		this.heading = heading;
	}

	/** Where it's being steered, for the log. */
	public String headingSpot(){
		return heading == null ? "nowhere" : Math.round(heading.x) + " " + Math.round(heading.z);
	}

	/** Where the bow is: the middle of its leading edge. */
	public Vec3 bow() {
		return position().add(Vec3.directionFromRotation(0, getYRot()).scale(BOW_REACH));
	}

	/** The spot {@code ahead} blocks beyond the bow of a barge here, facing this way. */
	public static Vec3 beyondBow(Vec3 position, float yaw, double ahead) {
		return position.add(Vec3.directionFromRotation(0, yaw).scale(BOW_REACH + ahead));
	}

	/** Whether the bow is up against something that isn't open water (its hull box is shorter than its hull: it stops short of it). */
	public boolean isBlockedAhead() {
		return blockedAhead;
	}

	/** Backs off a block (his seat is in the bow: pushed into a bank, he'd be crushed). */
	public void backOff() {
		Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-1.0);
		setPos(getX() + back.x, getY(), getZ() + back.z);
		speed = 0;
	}

	/** The middle of the deck, as drawn: the hull's depth above the barge's position. */
	public Vec3 deck() {
		return position().add(0, HULL_DEPTH, 0);
	}

	/** Where something stands or sits aboard, given how far forward of the middle and how high. */
	public static Vec3 aboard(Vec3 middle, float yaw, double forward, double up) {
		return middle.add(Vec3.directionFromRotation(0, yaw).scale(forward)).add(0, up, 0);
	}

	/**
	 * How much of its top speed it keeps while turning: full ahead when it's facing the way it wants to go, easing
	 * off as the turn gets sharper (down to 30% for a right angle or more), so it can take a bend.
	 */
	public static double turningSpeed(float turnLeft) {
		return Mth.clamp(1 - Math.abs(turnLeft) / 90.0, 0.3, 1.0);
	}

	/** How far ahead of its hull it cuts through water plants. */
	public static final double CUTS_AHEAD = 1.5;

	/** Kelp, seagrass and lily pads in its way are cut away (nothing dropped), so they never hold it up. */
	private void cutPlants(ServerLevel level, Vec3 forward) {
		AABB sweep = getBoundingBox().expandTowards(forward.normalize().scale(CUTS_AHEAD));
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(sweep.minX), Mth.floor(sweep.minY) - 1, Mth.floor(sweep.minZ),
				Mth.floor(sweep.maxX), Mth.floor(sweep.maxY), Mth.floor(sweep.maxZ)))
			if (Crossing.isWaterPlant(level.getBlockState(pos)))
				level.destroyBlock(pos, false);
	}

	/** The way to turn this tick to face a heading, at most {@link #TURN_PER_TICK} degrees. */
	public static float turnToward(float yaw, float wanted) {
		return yaw + Mth.clamp(Mth.wrapDegrees(wanted - yaw), -TURN_PER_TICK, TURN_PER_TICK);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			lerp();
			rowAnimation.animateWhen(isRowing(), tickCount);
			idleAnimation.animateWhen(!isRowing(), tickCount);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (tickCount % CHECK_INTERVAL == 0 && getPassengers().stream().noneMatch(p -> p instanceof PostmanEntity rider && rider.isAlive())) {
			vanish(level);
			return;
		}
		row(level);
	}

	private void row(ServerLevel level) {
		Vec3 motion = getDeltaMovement();
		if (heading != null && Journey.horizontalDistance(position(), heading) > 1.0) {
			float wanted = DeliveryRun.yawToward(position(), heading);
			setYRot(turnToward(getYRot(), wanted));
			double top = speed() * turningSpeed(Mth.wrapDegrees(wanted - getYRot()));
			speed = speed > top ? Math.max(top, speed - 2 * ACCELERATION) : Math.min(top, speed + ACCELERATION);
		} else {
			speed *= 0.8;
		}
		// The hull is longer than its box: it stops with the bow short of anything that isn't open water ahead, rather
		// than push its bow (and the rider's seat in it) into a bank, a wall or a low roof
		Vec3 ahead = beyondBow(position(), getYRot(), STOPS_SHORT);
		blockedAhead = Double.isNaN(Crossing.surfaceIn(level, Mth.floor(ahead.x), Mth.floor(ahead.z), Mth.floor(getY())));
		if (blockedAhead && speed > 0)
			speed = 0;
		Vec3 forward = Vec3.directionFromRotation(0, getYRot()).scale(speed);
		// Float: hold the deck just above the water; with no water underneath it settles onto whatever is there
		double surface = Crossing.surfaceIn(level, Mth.floor(getX()), Mth.floor(getZ()), Mth.floor(getY()));
		double dy = Double.isNaN(surface) ? Math.max(motion.y - 0.04, -0.5) : Mth.clamp((floatHeight(surface) - getY()) * 0.3, -0.1, 0.1);
		setDeltaMovement(forward.x, dy, forward.z);
		move(MoverType.SELF, getDeltaMovement());
		boolean rowing = speed > 0.02;
		if (rowing)
			cutPlants(level, forward);
		entityData.set(DATA_ROWING, rowing);
		if (rowing && tickCount % STROKE_TICKS == 0)
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.8F, 0.9F + random.nextFloat() * 0.2F);
	}

	/** Gone in a puff of smoke, along with anything still aboard that has no business being left behind. */
	public void vanish(ServerLevel level) {
		for (Entity passenger : getPassengers()) {
			passenger.stopRiding();
			if (passenger instanceof PostmanHorse horse) {
				PostmanEffects.puff(level, horse);
				horse.discard();
			}
		}
		PostmanEffects.puff(level, this);
		discard();
	}

	// ---------------------------------------------------------------- who sits where

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < 2;
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction move) {
		if (!hasPassenger(passenger))
			return;
		Vec3 spot = passenger instanceof PostmanEntity
			? aboard(deck(), getYRot(), BENCH_FORWARD, BENCH_HEIGHT - HIP_HEIGHT)
			: aboard(deck(), getYRot(), DECK_FORWARD, DECK_HEIGHT);
		move.accept(passenger, spot.x, spot.y, spot.z);
		passenger.setYRot(getYRot());
		if (passenger instanceof LivingEntity living) {
			living.setYBodyRot(getYRot());
			living.setYHeadRot(getYRot());
		}
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		return null;
	}

	// ---------------------------------------------------------------- smooth movement for everyone watching

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYRot = yRot;
		lerpSteps = steps + 2;
	}

	private void lerp() {
		if (lerpSteps <= 0)
			return;
		double t = 1.0 / lerpSteps;
		setPos(getX() + (lerpX - getX()) * t, getY() + (lerpY - getY()) * t, getZ() + (lerpZ - getZ()) * t);
		setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) * (float) t);
		lerpSteps--;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		// It's five blocks long and the lantern post stands two blocks high
		return getBoundingBox().inflate(1.5, 2.0, 1.5);
	}

	// ---------------------------------------------------------------- it can't be harmed or moved about

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {}
}
