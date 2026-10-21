package com.comedymonkey.ponyexpress.content.lost;

import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.post.Mail;
import com.comedymonkey.ponyexpress.post.PendingDelivery;
import com.comedymonkey.ponyexpress.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
		BlockPos spot = findSpot(near, pos -> canHold(level, pos));
		if (spot == null) {
			for (ItemStack item : delivery.items())
				Containers.dropItemStack(level, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, item.copy());
			return null;
		}
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
		level.setBlock(spot, ModBlocks.LOST_PARCEL.get().defaultBlockState()
			.setValue(LostParcelBlock.FACING, facing).setValue(LostParcelBlock.CAUSE, cause), Block.UPDATE_ALL);
		if (level.getBlockEntity(spot) instanceof LostParcelBlockEntity parcel)
			parcel.fill(delivery.items(), delivery.sender(), delivery.senderName(), delivery.fromName(), delivery.toName(),
				recipient, killer, killerName, Mail.day(level.getDayTime()));
		return spot;
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
