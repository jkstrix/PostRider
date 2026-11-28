package com.comedymonkey.postrider.content.postman;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRiderConfig;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
	/** How much faster it goes sliding on ice than rowing on water. */
	public static final double ICE_GLIDE = 1.5;
	/** How far beyond the bow it looks for ice to rise onto, and how far in from the hull's sides its corners look. */
	public static final double ICE_LOOKAHEAD = 3.0, CORNER_INSET = 0.2;
	/** How fast at most the hull rises to ice ahead, a block up at most, in blocks a tick. */
	public static final double RISE_PER_TICK = 0.25;
	/** Whether the hull is on ice this tick (sliding, not rowing). */
	private boolean onIce;
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

	/**
	 * The height the water (or ice) it's on is looked for near: a touch under the hull, so that afloat (the hull's
	 * bottom pixel in the water) and on ice (the hull on top of it) alike it's the level of that surface's block,
	 * and ice a block higher than the surface it's on is a bank, not a step to climb.
	 */
	public static int levelBelow(double y) {
		return Mth.floor(y - 0.1);
	}

	/** As {@link #levelBelow}, for where it is now. */
	public int waterLevelY() {
		return levelBelow(getY());
	}

	public boolean isOnIce() {
		return onIce;
	}

	/**
	 * How far above the ice's top the hull is held: resting exactly on it, a hair under from the easing up to it, the
	 * hull's box overlaps the ice and every move bumps against it.
	 */
	public static final double ICE_CLEARANCE = 0.02;

	/** Where the hull rests on ice whose top is at this height. */
	public static double iceHeight(double top) {
		return top + ICE_CLEARANCE;
	}

	/** Where the hull rests: where the surface under it puts it, or up over the top of ice ahead when that's higher. */
	public static double restHeight(double here, double iceAhead) {
		return Double.isNaN(iceAhead) ? here : Math.max(here, iceHeight(iceAhead));
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
		if (level().isClientSide()) {
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
		int nearY = waterLevelY();
		Vec3 ahead = beyondBow(position(), getYRot(), STOPS_SHORT);
		blockedAhead = Double.isNaN(Crossing.surfaceIn(level, Mth.floor(ahead.x), Mth.floor(ahead.z), nearY));
		if (blockedAhead && speed > 0)
			speed = 0;
		// Float: hold the deck just above the water, or the hull on top of the ice it's slid onto (a shade faster
		// there); with neither underneath it settles onto whatever is there
		double surface = Crossing.surfaceIn(level, Mth.floor(getX()), Mth.floor(getZ()), nearY);
		onIce = !Double.isNaN(surface) && Crossing.isIceSurface(level, Mth.floor(getX()), Mth.floor(getZ()), surface);
		if (onIce)
			speed = Math.min(speed * ICE_GLIDE, speed() * ICE_GLIDE);
		Vec3 forward = Vec3.directionFromRotation(0, getYRot()).scale(speed);
		// Ice ahead at the water's level: the hull rises to the ice's top before it gets there, so it slides up onto
		// it rather than bump against its edge (afloat its hull sits a pixel lower than the ice)
		// Looked for at the bow's middle and both its corners, so a sheet met at an angle is climbed as well as one
		// met square on (the hull is wide, and a corner meets the ice first)
		double iceAhead = Double.NaN;
		Vec3 forwards = Vec3.directionFromRotation(0, getYRot());
		for (double side : new double[] {0, BOX_WIDTH / 2 - CORNER_INSET, -(BOX_WIDTH / 2 - CORNER_INSET)}) {
			Vec3 look = position().add(forwards.scale(BOW_REACH + ICE_LOOKAHEAD)).add(-forwards.z * side, 0, forwards.x * side);
			double there = Crossing.surfaceIn(level, Mth.floor(look.x), Mth.floor(look.z), nearY);
			if (!Double.isNaN(there) && Crossing.isIceSurface(level, Mth.floor(look.x), Mth.floor(look.z), there))
				iceAhead = Double.isNaN(iceAhead) ? there : Math.max(iceAhead, there);
		}
		double rest = restHeight(onIce ? iceHeight(surface) : floatHeight(surface), iceAhead);
		// Eased towards where it rests, and set there once it's close (eased only, it never quite gets there)
		// (Rising to ice a block up takes a few ticks at most, or the hull meets the step before it's up)
		double dy = Double.isNaN(surface) ? Math.max(motion.y - 0.04, -0.5) : Math.abs(rest - getY()) < 0.1 ? rest - getY() : Mth.clamp((rest - getY()) * 0.3, -0.1, RISE_PER_TICK);
		setDeltaMovement(forward.x, dy, forward.z);
		move(MoverType.SELF, getDeltaMovement());
		boolean rowing = speed > 0.02;
		if (rowing && !onIce)
			cutPlants(level, forward);
		entityData.set(DATA_ROWING, rowing);
		if (rowing && !onIce && tickCount % STROKE_TICKS == 0)
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

	/** Each update from the server is eased into over this many ticks. */
	public static final int LERP_STEPS = 5;

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, LERP_STEPS);
	}

	/** The box its renderer culls by. */
	public AABB getBoundingBoxForCulling() {
		// It's five blocks long and the lantern post stands two blocks high
		return getBoundingBox().inflate(1.5, 2.0, 1.5);
	}

	// ---------------------------------------------------------------- it can't be harmed or moved about

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
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
	protected void readAdditionalSaveData(ValueInput input) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {}
}
