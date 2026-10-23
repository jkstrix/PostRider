package com.comedymonkey.postrider.content.postman;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * The sums for crossing water by barge: when a land detour stops being worth it, where to launch, where to steer on
 * the water (well clear of ocean monuments and drowned) and where to step ashore.
 */
public final class Crossing {
	/** Riding round is worth it unless it's more than twice as long as the route with the barge. */
	public static final double DETOUR_FACTOR = 2.0;
	/** How far ahead from the water's edge he looks for open water to launch the barge into. */
	public static final int LAUNCH_REACH = 8;
	/** Open water needed on every side of the launch spot: the barge is 3 blocks wide and 5 long. */
	public static final int LAUNCH_CLEARANCE = 2;
	/** How far from the bow he looks for dry land to step off onto. */
	public static final int LANDING_REACH = 5;
	/** Ocean monuments get this wide a berth, out of their guardians' reach. */
	public static final int MONUMENT_BERTH = 24;
	/** Drowned get this wide a berth: out of reach of their grabbing, if not always of a thrown trident. */
	public static final double DROWNED_BERTH = 12;

	private Crossing() {}

	/** The height of the water's surface in a column, or NaN if it isn't open water (land, or water under a roof). */
	@FunctionalInterface
	public interface Water {
		double surface(int x, int z);
	}

	/** The first open water ahead, towards the destination, with room all round for the barge; null if there's none. */
	@Nullable
	public static Vec3 launchSpot(Vec3 from, Vec3 target, Water water) {
		double distance = Journey.horizontalDistance(from, target);
		if (distance < 1e-6)
			return null;
		double dx = (target.x - from.x) / distance, dz = (target.z - from.z) / distance;
		for (int step = 1; step <= LAUNCH_REACH; step++) {
			int x = Mth.floor(from.x + dx * step), z = Mth.floor(from.z + dz * step);
			if (isOpen(x, z, water))
				return new Vec3(x + 0.5, water.surface(x, z), z + 0.5);
		}
		return null;
	}

	private static boolean isOpen(int x, int z, Water water) {
		for (int ox = -LAUNCH_CLEARANCE; ox <= LAUNCH_CLEARANCE; ox++)
			for (int oz = -LAUNCH_CLEARANCE; oz <= LAUNCH_CLEARANCE; oz++)
				if (Double.isNaN(water.surface(x + ox, z + oz)))
					return false;
		return true;
	}

	/**
	 * Where to steer next: the first of the usual aims (straight on, then swerves) that's safe land or open water,
	 * skipping any too near danger (an ocean monument, drowned); null if none is.
	 */
	@Nullable
	public static Vec3 waterAim(Vec3 from, Vec3 target, Predicate<Vec3> isLand, Predicate<Vec3> isWater, Predicate<Vec3> nearDanger) {
		for (Vec3 aim : Journey.aims(from, target))
			if (!nearDanger.test(aim) && (isLand.test(aim) || isWater.test(aim)))
				return aim;
		return null;
	}

	/** Whether there's open water on the straight line from here towards somewhere, within this many blocks. */
	public static boolean waterAhead(Vec3 from, Vec3 toward, double within, Water water) {
		double distance = Journey.horizontalDistance(from, toward);
		if (distance < 1e-6)
			return false;
		double dx = (toward.x - from.x) / distance, dz = (toward.z - from.z) / distance;
		for (int step = 1; step <= Math.min(within, distance); step++)
			if (!Double.isNaN(water.surface(Mth.floor(from.x + dx * step), Mth.floor(from.z + dz * step))))
				return true;
		return false;
	}

	/** Whether a spot is within some distance (across the water) of any of these. */
	public static boolean nearAny(Vec3 spot, List<Vec3> dangers, double berth) {
		for (Vec3 danger : dangers)
			if (Journey.horizontalDistance(spot, danger) < berth)
				return true;
		return false;
	}

	/** Whether a body on foot can pass through a column at a height: nothing solid where his feet and head would be. */
	public interface Way {
		boolean clear(int x, int z, int feetY);

		/** Open everywhere (for made-up ground in tests). */
		Way OPEN = (x, z, y) -> true;
	}

	/** The dry ground nearest the bow (and, among equals, nearest the destination) to step off onto; null if none. */
	@Nullable
	public static BlockPos landingSpot(Vec3 bow, Vec3 target, CampSites.Floor floor) {
		return landingSpot(bow, target, floor, LANDING_REACH, Way.OPEN);
	}

	/** As above, looking up to {@code reach} blocks from the bow (further than usual when he's stuck against a bank). */
	@Nullable
	public static BlockPos landingSpot(Vec3 bow, Vec3 target, CampSites.Floor floor, int reach) {
		return landingSpot(bow, target, floor, reach, Way.OPEN);
	}

	/**
	 * As above, and only ground he can get to from the bow: nothing in the way between (a wall along the shore, a
	 * bank too high) at the height he'd walk it.
	 */
	@Nullable
	public static BlockPos landingSpot(Vec3 bow, Vec3 target, CampSites.Floor floor, int reach, Way way) {
		int bx = Mth.floor(bow.x), bz = Mth.floor(bow.z);
		for (int radius = 0; radius <= reach; radius++) {
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int dx = -radius; dx <= radius; dx++)
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
						continue;
					int y = floor.at(bx + dx, bz + dz);
					if (y == CampSites.UNUSABLE || !wayClear(bx, bz, bx + dx, bz + dz, y, way))
						continue;
					double distance = Journey.horizontalDistance(new Vec3(bx + dx + 0.5, y, bz + dz + 0.5), target);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = new BlockPos(bx + dx, y, bz + dz);
					}
				}
			if (best != null)
				return best;
		}
		return null;
	}

	/** Whether the straight line between two columns is clear at a height, the columns between them inclusive. */
	static boolean wayClear(int fromX, int fromZ, int toX, int toZ, int feetY, Way way) {
		int steps = Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ));
		for (int i = 1; i < steps; i++) {
			int x = fromX + Math.round((float) (toX - fromX) * i / steps), z = fromZ + Math.round((float) (toZ - fromZ) * i / steps);
			if (!way.clear(x, z, feetY))
				return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- the real world

	/** Plants in the water that the barge cuts its way through, so they count as open water. */
	public static boolean isWaterPlant(BlockState state) {
		return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
			|| state.is(Blocks.LILY_PAD);
	}

	/** Water, by its tag (modded water too) or, before tags are loaded, by being vanilla water. */
	public static boolean isWater(FluidState fluid) {
		return fluid.is(FluidTags.WATER) || fluid.getType().isSame(Fluids.WATER);
	}

	private static boolean clear(BlockState state) {
		return state.isAir() || isWaterPlant(state);
	}

	/** How much clear air the barge needs over the water: the barge, the horse on its deck and the rider on it. */
	public static final int HEADROOM = 3;

	/**
	 * The water's surface in a column near a height, if it's open above: nothing but air (or water plants) for
	 * {@link #HEADROOM} blocks over it, so the barge passes under no bridge, overhanging tree or cave roof that would
	 * crush him and his horse against it. NaN otherwise.
	 */
	public static double surfaceIn(BlockGetter level, int x, int z, int nearY) {
		for (int y = nearY + 2; y >= nearY - 4; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			FluidState fluid = level.getFluidState(pos);
			if (isWater(fluid) && level.getFluidState(pos.above()).isEmpty() && headroomOver(level, pos))
				return y + fluid.getHeight(level, pos);
			if (!clear(level.getBlockState(pos)) && !isWater(fluid))
				return Double.NaN;
		}
		return Double.NaN;
	}

	/** Whether the {@link #HEADROOM} blocks over a water block are clear. */
	public static boolean headroomOver(BlockGetter level, BlockPos water) {
		for (int up = 1; up <= HEADROOM; up++)
			if (!clear(level.getBlockState(water.above(up))))
				return false;
		return true;
	}

	/** Whether a spot is within {@link #MONUMENT_BERTH} blocks of an ocean monument (as far as loaded chunks tell). */
	public static boolean nearMonument(ServerLevel level, Vec3 spot) {
		Structure monument = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.OCEAN_MONUMENT);
		if (monument == null)
			return false;
		int y = level.getSeaLevel() - 8;
		for (int[] offset : new int[][] {{0, 0}, {MONUMENT_BERTH, 0}, {-MONUMENT_BERTH, 0}, {0, MONUMENT_BERTH}, {0, -MONUMENT_BERTH}}) {
			BlockPos pos = BlockPos.containing(spot.x + offset[0], y, spot.z + offset[1]);
			if (level.isLoaded(pos) && level.structureManager().getStructureAt(pos, monument).isValid())
				return true;
		}
		return false;
	}
}
