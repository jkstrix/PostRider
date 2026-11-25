package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/** He's held to a walk near fire and the like, so his horse doesn't graze it in passing. */
class HazardsTest {
	/** Grass with its top at y 64, and whatever's been put on it. */
	private static final class Field implements BlockGetter {
		final Map<BlockPos, BlockState> put = new HashMap<>();

		@Override
		public BlockState getBlockState(BlockPos pos) {
			BlockState state = put.get(pos);
			if (state != null)
				return state;
			return pos.getY() < 64 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		@Nullable
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinY() {
			return -64;
		}
	}

	/** A horse (1.4 wide, 1.6 tall) standing with its middle here, on the grass. */
	private static AABB horse(double x, double z) {
		double half = 1.3964844 / 2;
		return new AABB(x - half, 64, z - half, x + half, 65.6, z + half);
	}

	@Test
	void openGroundIsNoReasonToSlowDown() {
		assertFalse(Hazards.near(new Field(), horse(0, 0)));
	}

	@Test
	void fireABlockOrTwoOffSlowsTheHorse() {
		Field field = new Field();
		field.put.put(new BlockPos(2, 64, 0), Blocks.FIRE.defaultBlockState());
		// The horse reaches 0.7 either side of its middle: the fire's near side is 1.3 off, then 1.9 off (in reach)
		assertTrue(Hazards.near(field, horse(0, 0)));
		assertTrue(Hazards.near(field, horse(-0.6, 0)));
	}

	@Test
	void fireFurtherOffDoesNot() {
		Field field = new Field();
		field.put.put(new BlockPos(4, 64, 0), Blocks.FIRE.defaultBlockState());
		// The fire's near side is 3.3 off the horse
		assertFalse(Hazards.near(field, horse(0, 0)));
	}

	@Test
	void aCampfireOrCactusBesideItDoesToo() {
		Field campfire = new Field();
		campfire.put.put(new BlockPos(0, 64, -2), Blocks.CAMPFIRE.defaultBlockState());
		assertTrue(Hazards.near(campfire, horse(0, 0)));
		Field cactus = new Field();
		cactus.put.put(new BlockPos(-2, 64, 1), Blocks.CACTUS.defaultBlockState());
		assertTrue(Hazards.near(cactus, horse(0, 0)));
	}

	@Test
	void magmaInTheGroundBesideItDoesToo() {
		Field field = new Field();
		field.put.put(new BlockPos(1, 63, 1), Blocks.MAGMA_BLOCK.defaultBlockState());
		assertTrue(Hazards.near(field, horse(0, 0)));
	}
}
