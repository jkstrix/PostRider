package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.Navigator;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

class GapJumpTest {
	private static final Vec3 TAKE_OFF = new Vec3(10, 64, 10), LANDING = new Vec3(14, 64, 10);

	@Test
	void heTakesOffAtTheEdgeHisPlanLeapsFrom() {
		assertTrue(DeliveryRun.readyToLeap(new Vec3(9.5, 64, 10), TAKE_OFF, LANDING), "coming up to the edge");
		assertTrue(DeliveryRun.readyToLeap(TAKE_OFF, TAKE_OFF, LANDING));
		assertFalse(DeliveryRun.readyToLeap(new Vec3(6, 64, 10), TAKE_OFF, LANDING), "not yet");
		assertFalse(DeliveryRun.readyToLeap(new Vec3(11, 64, 10), TAKE_OFF, LANDING), "already out over the gap");
	}

	@Test
	void aWiderGapGetsAHarderPush() {
		assertTrue(DeliveryRun.leapPush(4) > DeliveryRun.leapPush(2));
		assertTrue(DeliveryRun.leapPush(1) >= 0.35, "even a short hop clears the edge");
	}

	@Test
	void landingPastWhereTheLeapEndsHeRidesOnRatherThanTurningBack() {
		// Steps at x 14 (where the leap lands) and x 15: having landed at x 14.8, he's past the first
		assertTrue(Navigator.passed(14.8, 10, 14, 10, 15, 10));
		// Short of it, or off to the side behind it, he isn't
		assertFalse(Navigator.passed(13.2, 10, 14, 10, 15, 10));
		assertFalse(Navigator.passed(14.2, 12, 14, 10, 15, 10), "nearer the step he's heading for than the one after");
	}

	@Test
	void knowsWhatGroundIsDangerous() {
		assertTrue(Hazards.isDangerous(Blocks.LAVA.defaultBlockState()));
		assertTrue(Hazards.isDangerous(Blocks.MAGMA_BLOCK.defaultBlockState()));
		assertTrue(Hazards.isDangerous(Blocks.FIRE.defaultBlockState()));
		assertTrue(Hazards.isDangerous(Blocks.CAMPFIRE.defaultBlockState()));
		assertTrue(Hazards.isDangerous(Blocks.CACTUS.defaultBlockState()));
		assertTrue(Hazards.isDangerous(Blocks.POWDER_SNOW.defaultBlockState()));
		assertFalse(Hazards.isDangerous(Blocks.GRASS_BLOCK.defaultBlockState()));
		assertFalse(Hazards.isDangerous(Blocks.SAND.defaultBlockState()));
		assertFalse(Hazards.isDangerous(Blocks.AIR.defaultBlockState()));
	}

	@Test
	void theBargeCutsThroughWaterPlants() {
		assertTrue(Crossing.isWaterPlant(Blocks.KELP.defaultBlockState()));
		assertTrue(Crossing.isWaterPlant(Blocks.KELP_PLANT.defaultBlockState()));
		assertTrue(Crossing.isWaterPlant(Blocks.SEAGRASS.defaultBlockState()));
		assertTrue(Crossing.isWaterPlant(Blocks.TALL_SEAGRASS.defaultBlockState()));
		assertTrue(Crossing.isWaterPlant(Blocks.LILY_PAD.defaultBlockState()));
		assertFalse(Crossing.isWaterPlant(Blocks.OAK_PLANKS.defaultBlockState()), "but not a dock");
		assertFalse(Crossing.isWaterPlant(Blocks.STONE.defaultBlockState()));
	}
}
