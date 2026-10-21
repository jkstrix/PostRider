package com.comedymonkey.ponyexpress.content.postman;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Finding somewhere to camp: a flat patch big enough for the tent (pegs and ropes included) with a spot to sit in
 * front of it, and no drop beside it, so he never pitches on the lip of a ravine.
 */
public final class CampSites {
	/** How far from where night falls he'll look for a pitch. */
	public static final int SEARCH_RADIUS = 8;
	/** The tent's footprint behind his seat: this many blocks long (pegs to pegs) and this far either side. */
	public static final int TENT_LENGTH = 4;
	public static final int TENT_HALF_WIDTH = 1;
	/** Ground this far around the pitch may be at most one block lower; any more and it's a drop. */
	public static final int MARGIN = 1;
	/** What {@link Floor} returns for a column he can't use (no ground, lava, water...). */
	public static final int UNUSABLE = Integer.MIN_VALUE;

	/** Where he sits (facing the way he's looking) and where the middle of the tent goes, behind him. */
	public record Site(BlockPos seat, Direction facing) {
		public Vec3 seatPos() {
			return Vec3.atBottomCenterOf(seat);
		}

		public Vec3 tentPos() {
			return seatPos().subtract(facing.getStepX() * DeliveryRun.TENT_DISTANCE, 0, facing.getStepZ() * DeliveryRun.TENT_DISTANCE);
		}

		public float yaw() {
			return facing.toYRot();
		}
	}

	/** The height he'd stand at in a column, or {@link #UNUSABLE}. */
	@FunctionalInterface
	public interface Floor {
		int at(int x, int z);
	}

	/** The nearest good pitch to the origin, or null if there's none within {@link #SEARCH_RADIUS}. */
	@Nullable
	public static Site find(BlockPos origin, Floor floor) {
		for (int radius = 0; radius <= SEARCH_RADIUS; radius++)
			for (int dx = -radius; dx <= radius; dx++)
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
						continue;
					for (Direction facing : Direction.Plane.HORIZONTAL) {
						Site site = check(origin.getX() + dx, origin.getZ() + dz, facing, floor);
						if (site != null)
							return site;
					}
				}
		return null;
	}

	@Nullable
	static Site check(int x, int z, Direction facing, Floor floor) {
		int height = floor.at(x, z);
		if (height == UNUSABLE)
			return null;
		int ax = -facing.getStepX(), az = -facing.getStepZ();
		int sx = -az, sz = ax;
		// From two blocks in front of his seat to a block past the back pegs, and across the tent's width plus a margin
		for (int along = -2; along <= TENT_LENGTH + MARGIN; along++) {
			for (int across = -TENT_HALF_WIDTH - MARGIN; across <= TENT_HALF_WIDTH + MARGIN; across++) {
				int cx = x + ax * along + sx * across, cz = z + az * along + sz * across;
				int here = floor.at(cx, cz);
				boolean onPitch = along >= 0 && along <= TENT_LENGTH && Math.abs(across) <= TENT_HALF_WIDTH;
				if (onPitch ? here != height : (here == UNUSABLE || here < height - 1 || here > height + 1))
					return null;
			}
		}
		return new Site(new BlockPos(x, height, z), facing);
	}

	/** The floor in a column of the real world near a height, or unusable if there's none or it's dangerous. */
	public static int floorIn(ServerLevel level, int x, int z, int nearY) {
		for (int y = nearY + 2; y >= nearY - 3; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
					&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty())
				return Hazards.isDangerous(level, feet) ? UNUSABLE : y;
		}
		return UNUSABLE;
	}
}
