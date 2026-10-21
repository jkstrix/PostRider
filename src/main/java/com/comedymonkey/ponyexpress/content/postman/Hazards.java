package com.comedymonkey.ponyexpress.content.postman;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Ground a rider won't aim for, jump onto or be put down on: anything that burns, hurts or swallows him. */
public final class Hazards {
	private Hazards() {}

	/** Whether standing at this spot (feet in it, the block under it the floor) would hurt or sink him. */
	public static boolean isDangerous(BlockGetter level, BlockPos feet) {
		return isDangerous(level.getBlockState(feet)) || isDangerous(level.getBlockState(feet.below()))
			|| !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty();
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
