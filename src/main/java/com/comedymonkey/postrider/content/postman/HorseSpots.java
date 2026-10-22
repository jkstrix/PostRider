package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds room beside the rider for his horse to appear, so it never turns up inside a wall. */
public final class HorseSpots {
	/** How far beside him the horse appears. */
	public static final double DISTANCE = 2.0;
	private static final double[] HEIGHTS = {0, 1, -1};

	private HorseSpots() {}

	/** Places to try, best first: his right, his left, behind, in front, then where he stands; each level, up, down. */
	public static List<Vec3> candidates(Vec3 rider, float yaw) {
		List<Vec3> sides = new ArrayList<>();
		for (float turn : new float[] {90, -90, 180, 0}) {
			Vec3 offset = Vec3.directionFromRotation(0, yaw + turn).scale(DISTANCE);
			sides.add(new Vec3(offset.x, 0, offset.z));
		}
		sides.add(Vec3.ZERO);
		List<Vec3> spots = new ArrayList<>();
		for (Vec3 side : sides)
			for (double dy : HEIGHTS)
				spots.add(rider.add(side.x, dy, side.z));
		return spots;
	}

	@Nullable
	public static Vec3 find(Vec3 rider, float yaw, Predicate<Vec3> fits) {
		for (Vec3 spot : candidates(rider, yaw))
			if (fits.test(spot))
				return spot;
		return null;
	}

	/** Room for the whole horse, nothing solid or wet in the way, and ground to stand on under all of it. */
	public static boolean fits(ServerLevel level, EntityType<?> horse, Vec3 spot) {
		AABB box = horse.getSpawnAABB(spot.x, spot.y, spot.z);
		if (!level.noCollision(box) || level.containsAnyLiquid(box))
			return false;
		BlockPos ground = BlockPos.containing(spot.x, spot.y - 0.5, spot.z);
		if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP))
			return false;
		// And solid ground under every corner of it: a horse put down on a bank edge walks off into the river, and
		// one on the edge of a bridge or a cliff is half over the drop
		for (double cx : new double[] {box.minX + 0.05, box.maxX - 0.05})
			for (double cz : new double[] {box.minZ + 0.05, box.maxZ - 0.05}) {
				BlockPos corner = BlockPos.containing(cx, spot.y - 0.5, cz);
				if (!level.getFluidState(corner).isEmpty() || Hazards.isDangerous(level.getBlockState(corner))
						|| !level.getBlockState(corner).isFaceSturdy(level, corner, Direction.UP))
					return false;
			}
		return true;
	}
}
