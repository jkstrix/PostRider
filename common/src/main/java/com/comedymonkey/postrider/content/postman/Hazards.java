package com.comedymonkey.postrider.content.postman;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Ground a rider won't aim for, jump onto or be put down on: anything that burns, hurts or swallows him. */
public final class Hazards {
	private Hazards() {}

	/** Whether standing at this spot (feet in it, the block under it the floor) would hurt or sink him. */
	public static boolean isDangerous(BlockGetter level, BlockPos feet) {
		return isDangerous(level.getBlockState(feet)) || isDangerous(level.getBlockState(feet.below()))
			|| !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty();
	}

	/**
	 * How near something dangerous (fire, a campfire, cactus...) he or his horse is held to a walk. Since 26.1 a body
	 * is hurt by anything it brushes on its way through a tick, not only by what it ends the tick in, so at a gallop a
	 * stride that ends clear of a fire can still have grazed it (the horse stands only about 0.3 of a block inside its
	 * 2x2 blocks). Two blocks gives a galloping horse room to slow down before it's alongside.
	 */
	public static final double WALK_REACH = 2.0;

	/** Whether anything dangerous is within {@link #WALK_REACH} of this body (and in the blocks under it). */
	public static boolean near(BlockGetter level, AABB body) {
		for (BlockPos pos : BlockPos.betweenClosed(body.inflate(WALK_REACH, 0, WALK_REACH).expandTowards(0, -1, 0)))
			if (isDangerous(level.getBlockState(pos)))
				return true;
		return false;
	}

	public static boolean isDangerous(BlockState state) {
		// The tags catch modded fires and campfires; the vanilla ones are named too, as tags only load with a world
		return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
			|| state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
			|| state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.LAVA)
			|| state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.COBWEB);
	}
}
