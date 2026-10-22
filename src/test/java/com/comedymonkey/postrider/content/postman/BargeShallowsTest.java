package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.Navigator;
import com.comedymonkey.postrider.content.postman.route.WaterPlanner;
import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The barge on a river only one block deep: it sees the water, launches onto it, plans across it and floats clear of the bed. */
class BargeShallowsTest {
	/** The river's water is in the blocks at this height, over a stone bed; the banks are grass at the same height. */
	private static final int WATER_Y = 62;
	private static final double SURFACE = WATER_Y + 8 / 9.0;

	/** A river running east-west, 11 blocks wide (z -5 to 5) and one block deep, with a row of lily pads across it at x 25. */
	private static final class Shallows implements BlockGetter {
		static boolean river(int z) {
			return z >= -5 && z <= 5;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			int y = pos.getY();
			if (y < WATER_Y)
				return Blocks.STONE.defaultBlockState();
			if (y == WATER_Y)
				return river(pos.getZ()) ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
			if (y == WATER_Y + 1 && pos.getX() == 25 && river(pos.getZ()))
				return Blocks.LILY_PAD.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
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
		public int getMinBuildHeight() {
			return -64;
		}
	}

	private static final Shallows SHALLOWS = new Shallows();
	private static final Crossing.Water WATER = (x, z) -> Crossing.surfaceIn(SHALLOWS, x, z, WATER_Y + 1);

	@Test
	void waterOneBlockDeepIsOpenWater() {
		assertEquals(SURFACE, WATER.surface(20, 0), 1e-6);
		assertEquals(SURFACE, WATER.surface(25, 0), 1e-6, "lily pads on it don't stop it being open water");
		assertTrue(Double.isNaN(WATER.surface(20, -8)), "the bank is land");
	}

	@Test
	void heLaunchesTheBargeOntoIt() {
		Vec3 launch = Crossing.launchSpot(new Vec3(20.5, 63, -7.5), new Vec3(20.5, 63, 40.5), WATER);
		assertNotNull(launch, "room for the barge on the shallows");
		assertEquals(SURFACE, launch.y, 1e-6);
	}

	@Test
	void itFloatsClearOfTheBedAndBumpsIntoTheBanks() {
		double bottom = BargeEntity.floatHeight(SURFACE);
		AABB hull = new AABB(18.5, bottom, -1.5, 21.5, bottom + ModEntities.BARGE.get().getHeight(), 1.5);
		AABB bed = new AABB(18, WATER_Y - 1, -2, 22, WATER_Y, 2);
		assertFalse(hull.intersects(bed), "the hull clears the river bed");
		AABB bank = new AABB(18, WATER_Y, 6, 22, WATER_Y + 1, 7);
		assertTrue(hull.move(0, 0, 6).intersects(bank), "it runs into the bank rather than over it");
	}

	@Test
	void itRowsAcrossAndAlongTheShallows() {
		WaterPlanner.Water open = (x, z) -> !Double.isNaN(WATER.surface(x, z));
		WaterPlanner.Danger none = (x, z) -> 0;
		// Across to the far bank
		List<Vec3> across = WaterPlanner.plan(open, none, 20, -3, 20.5, 7.5, Navigator.LANDING_RADIUS);
		assertFalse(across.isEmpty());
		assertTrue(Math.hypot(across.get(across.size() - 1).x - 20.5, across.get(across.size() - 1).z - 7.5) <= Navigator.LANDING_RADIUS);
		// And down the river, through the lily pads
		List<Vec3> along = WaterPlanner.plan(open, none, 5, 0, 40.5, 0.5, 2);
		assertFalse(along.isEmpty());
		assertTrue(along.get(along.size() - 1).x > 38, "all the way down");
		for (Vec3 p : along)
			assertTrue(open.open((int) Math.floor(p.x), (int) Math.floor(p.z)), "aground at " + p);
	}
}
