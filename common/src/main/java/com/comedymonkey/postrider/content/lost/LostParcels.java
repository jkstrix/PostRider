package com.comedymonkey.postrider.content.lost;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/** Leaves a Lost Parcel in the world for a delivery that failed. */
public final class LostParcels {
	/** How far from where it went wrong to look for a patch of ground to put the parcel on. */
	public static final int SEARCH_RADIUS = 4;
	private static final int[] HEIGHTS = {0, 1, -1, 2, -2, 3, -3};

	private LostParcels() {}

	/**
	 * Places a Lost Parcel holding the delivery's items as close to {@code near} as there's ground for it, and returns
	 * where. If there's nowhere to put it, the items are dropped there instead and the result is null.
	 */
	@Nullable
	public static BlockPos place(ServerLevel level, BlockPos near, PendingDelivery delivery, LostCause cause,
			@Nullable UUID recipient, @Nullable UUID killer, String killerName) {
		// Lost out on the water, it floats where he went down; otherwise it lands on the ground nearby, or failing that
		// floats on any water close by
		BlockPos afloat = findSpot(near, pos -> isOpenWater(level, pos));
		BlockPos spot = afloat != null && isOpenWater(level, near.atY(afloat.getY())) ? afloat : findSpot(near, pos -> canHold(level, pos));
		if (spot == null)
			spot = afloat;
		// Nothing at all near where he was lost (he fell into the void, say): the nearest land, or water, there is
		if (spot == null)
			spot = nearestLand(level, near);
		if (spot == null) {
			for (ItemStack item : delivery.items())
				Containers.dropItemStack(level, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, item.copy());
			return null;
		}
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
		Block block = isOpenWater(level, spot) ? ModBlocks.LOST_PARCEL_WRECK.get() : ModBlocks.LOST_PARCEL.get();
		level.setBlock(spot, block.defaultBlockState()
			.setValue(LostParcelBlock.FACING, facing).setValue(LostParcelBlock.CAUSE, cause), Block.UPDATE_ALL);
		if (level.getBlockEntity(spot) instanceof LostParcelBlockEntity parcel)
			parcel.fill(delivery.items(), delivery.sender(), delivery.senderName(), delivery.fromName(), delivery.toName(),
				recipient, killer, killerName, Mail.day(level.getOverworldClockTime()));
		return spot;
	}

	/** How far round he looks for land when there's none at all near where he was lost. */
	public static final int LAND_SEARCH_RADIUS = 64;

	/** The nearest loaded column with somewhere to put a parcel on top: dry ground, or failing that open water. */
	@Nullable
	static BlockPos nearestLand(ServerLevel level, BlockPos near) {
		return nearestColumn(near, LAND_SEARCH_RADIUS, (x, z) -> {
			if (!level.isLoaded(new BlockPos(x, near.getY(), z)))
				return null;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y <= level.getMinY())
				return null;
			BlockPos top = new BlockPos(x, y, z);
			if (canHold(level, top))
				return top;
			return isOpenWater(level, top.below()) ? top.below() : null;
		});
	}

	/**
	 * A spot from the nearest column (as the crow flies) that offers one, searching square rings outwards from the
	 * origin's column until they're further out than the best found so far.
	 */
	@Nullable
	public static BlockPos nearestColumn(BlockPos origin, int radius, BiFunction<Integer, Integer, BlockPos> spotIn) {
		BlockPos best = null;
		int bestDistanceSqr = Integer.MAX_VALUE;
		for (int r = 0; r <= radius && r * r <= bestDistanceSqr; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					int distanceSqr = dx * dx + dz * dz;
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r || distanceSqr >= bestDistanceSqr)
						continue;
					BlockPos spot = spotIn.apply(origin.getX() + dx, origin.getZ() + dz);
					if (spot != null) {
						best = spot;
						bestDistanceSqr = distanceSqr;
					}
				}
		return best;
	}

	/** The top of still water with open air above it, where a wreck can float. */
	static boolean isOpenWater(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).isSourceOfType(Fluids.WATER) && level.getBlockState(pos).is(Blocks.WATER)
			&& level.getBlockState(pos.above()).isAir();
	}

	/** Empty (or replaceable) space with solid ground under it, and no water or lava. */
	static boolean canHold(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() && state.getFluidState().isEmpty()
			&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	/**
	 * The nearest position that can hold a parcel: the spot itself first, then rings further out, trying a few blocks
	 * up and down at each so it lands on the ground nearby rather than in the air or underground.
	 */
	@Nullable
	public static BlockPos findSpot(BlockPos origin, Predicate<BlockPos> canHold) {
		for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
			for (int dy : HEIGHTS) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
							continue;
						BlockPos pos = origin.offset(dx, dy, dz);
						if (canHold.test(pos))
							return pos;
					}
				}
			}
		}
		return null;
	}
}
