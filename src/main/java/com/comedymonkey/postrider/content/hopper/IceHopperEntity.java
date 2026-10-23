package com.comedymonkey.postrider.content.hopper;

import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModItems;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The ice hopper: a boat for players with an upturned, iron-shod bow (blockbench/ice_hopper.bbmodel) that hops from
 * the water onto sea ice and slides on without stopping, and slips off the ice back into the water the same way. A
 * boat otherwise: rowed, two seats, fast on ice, and it comes back as its own item when broken.
 */
public class IceHopperEntity extends Boat {
	/** How far ahead of the middle the bow looks for ice to hop onto. */
	public static final double REACH = 1.0;
	/** Slower than this it's only drifting, and drifts against the ice like any boat (a single tick of rowing is enough: bumped against the ice it has no way on left). */
	public static final double MIN_HOP_SPEED = 0.03;
	/** How far in from the hull's sides the corners of the bow look for ice, so an angled approach hops as well as a square one. */
	public static final double CORNER_INSET = 0.15;
	/** Clear blocks needed over the ice: the hull and whoever's in it. */
	public static final int HEADROOM = 2;

	public IceHopperEntity(EntityType<? extends Boat> type, Level level) {
		super(type, level);
	}

	public IceHopperEntity(Level level, double x, double y, double z) {
		this(ModEntities.ICE_HOPPER.get(), level);
		setPos(x, y, z);
		xo = x;
		yo = y;
		zo = z;
	}

	@Override
	public Item getDropItem() {
		return ModItems.ICE_HOPPER.get();
	}

	/**
	 * How far above where it sits the hull is drawn afloat: a boat's box floats mostly under the water, so its hull
	 * is drawn this much higher to ride at the waterline (as a vanilla boat's is); on ice it sits where it is.
	 */
	public static final double AFLOAT_LIFT = 0.375;
	/** Where a passenger's seat is above the hull's bottom: the seats are drawn 4 to 6 pixels up, and he sits into them a little. */
	public static final double SEAT_HEIGHT = 0.3;
	/** Where the seats are fore and aft of the middle: the front seat for a lone rower, the back one for the passenger. */
	public static final double FRONT_SEAT = 0.25, BACK_SEAT = -0.5;

	/** How far above where it sits the hull is drawn now. */
	public double hullLift() {
		return hullLift(isInWater());
	}

	public static double hullLift(boolean afloat) {
		return afloat ? AFLOAT_LIFT : 0.0;
	}

	/** How far fore or aft of the middle a passenger sits: the first in the front seat, the second in the back. */
	public static double seatOffset(int passengers, int index) {
		return passengers > 1 && index > 0 ? BACK_SEAT : FRONT_SEAT;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double seat = seatOffset(getPassengers().size(), getPassengers().indexOf(passenger));
		return new Vec3(0.0, hullLift() + SEAT_HEIGHT, seat).yRot(-getYRot() * (float) (Math.PI / 180.0));
	}

	/** Whether whoever's rowing is rowing forward this tick (the keys the client sends each tick). */
	private boolean rowingForward;

	@Override
	public void setInput(boolean left, boolean right, boolean up, boolean down) {
		super.setInput(left, right, up, down);
		rowingForward = up && !down;
	}

	@Override
	public void tick() {
		// The way it had on before this tick's move: against the ice's edge the move itself stops it dead
		Vec3 wayOn = getDeltaMovement();
		super.tick();
		if (isControlledByLocalInstance())
			hop(wayOn);
	}

	/**
	 * The way the hop looks: the way it's moving if it has way on, else the way it's facing if it's being rowed
	 * forward (bumped against the ice it has no way on at all, and would never hop by its motion); null for neither.
	 */
	@Nullable
	public static Vec3 hopHeading(Vec3 motion, boolean rowingForward, float yaw) {
		double speed = motion.horizontalDistance();
		if (speed >= MIN_HOP_SPEED)
			return new Vec3(motion.x / speed, 0, motion.z / speed);
		return rowingForward ? Vec3.directionFromRotation(0, yaw) : null;
	}

	/**
	 * Under way in the water, or on ice, with ice ahead at its own level or one block up: up onto it, keeping way on.
	 * The bow's middle and both its corners look ahead, so it hops coming in at an angle too.
	 */
	private void hop(Vec3 motion) {
		Vec3 heading = hopHeading(motion, rowingForward, getYRot());
		if (heading == null || !(isInWater() || isOnIce()))
			return;
		double dx = heading.x, dz = heading.z;
		double half = getBbWidth() / 2 - CORNER_INSET;
		for (double side : new double[] {0, half, -half}) {
			Vec3 ahead = position().add(dx * REACH - dz * side, 0.1, dz * REACH + dx * side);
			double top = hopTop(level(), BlockPos.containing(ahead), getY());
			if (Double.isNaN(top))
				continue;
			// Up onto the ice's top, and a little way onto it, so the hull has ice under it to slide on (not just in front)
			Vec3 up = new Vec3(getX() + dx * ONTO, top, getZ() + dz * ONTO);
			if (!level().noCollision(this, getBoundingBox().move(up.subtract(position()))))
				continue;
			setPos(up.x, up.y, up.z);
			// Up and on with the way it had, or a push forward if it had none left
			double speed = Math.max(motion.horizontalDistance(), MIN_HOP_SPEED * 2);
			setDeltaMovement(dx * speed, 0, dz * speed);
			hasImpulse = true;
			return;
		}
	}

	/** Whether the hull is sat on ice (sliding, rather than afloat). */
	public boolean isOnIce() {
		return Crossing.isIce(level().getBlockState(BlockPos.containing(getX(), getY() - 0.1, getZ())));
	}

	/**
	 * The top of the ice to hop onto from a block ahead at the hull's level, or NaN: that block if it's ice, else the
	 * one above it (a single block of ice standing on the sheet, the foot of a berg); never ice it's already up on.
	 */
	public static double hopTop(BlockGetter level, BlockPos base, double y) {
		for (BlockPos ice : new BlockPos[] {base, base.above()})
			if (canHopOnto(level, ice))
				return ice.getY() + 1 > y + 0.05 ? ice.getY() + 1 : Double.NaN;
		return Double.NaN;
	}

	/** How far onto the ice the hull is carried as it hops up, so there's ice under it. */
	public static final double ONTO = 0.4;

	/** Whether the block is ice with room over it for the boat and its passengers. */
	public static boolean canHopOnto(BlockGetter level, BlockPos pos) {
		if (!Crossing.isIce(level.getBlockState(pos)))
			return false;
		for (int up = 1; up <= HEADROOM; up++)
			if (!level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty())
				return false;
		return true;
	}
}
