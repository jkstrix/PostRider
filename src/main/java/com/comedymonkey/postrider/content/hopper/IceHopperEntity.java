package com.comedymonkey.postrider.content.hopper;

import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModItems;

import net.minecraft.core.BlockPos;
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
	/** Slower than this it's only drifting, and drifts against the ice like any boat. */
	public static final double MIN_HOP_SPEED = 0.06;
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

	@Override
	public void tick() {
		// The way it had on before this tick's move: against the ice's edge the move itself stops it dead
		Vec3 wayOn = getDeltaMovement();
		super.tick();
		if (isControlledByLocalInstance())
			hop(wayOn);
	}

	/** Under way in the water with ice ahead at the water's level: up onto it, keeping way on. */
	private void hop(Vec3 motion) {
		double speed = motion.horizontalDistance();
		if (speed < MIN_HOP_SPEED || !isInWater())
			return;
		double dx = motion.x / speed, dz = motion.z / speed;
		Vec3 ahead = position().add(dx * REACH, 0.1, dz * REACH);
		BlockPos ice = BlockPos.containing(ahead);
		if (!canHopOnto(level(), ice))
			return;
		// Up onto the ice's top, and a little way onto it, so the hull has ice under it to slide on (not just in front)
		Vec3 up = new Vec3(getX() + dx * ONTO, ice.getY() + 1, getZ() + dz * ONTO);
		if (!level().noCollision(this, getBoundingBox().move(up.subtract(position()))))
			return;
		setPos(up.x, up.y, up.z);
		setDeltaMovement(motion.x, 0, motion.z);
		hasImpulse = true;
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
