package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;

class GapJumpTest {
	private static final boolean[] ALL_SAFE = {true, true, true, true};

	@Test
	void keepsGallopingOnFlatGround() {
		assertEquals(0, GapJump.landingDistance(64, new int[] {64, 64, 64, 64}, ALL_SAFE));
	}

	@Test
	void stepsDownAOneBlockDropWithoutJumping() {
		assertEquals(0, GapJump.landingDistance(64, new int[] {63, 63, 63, 63}, ALL_SAFE));
	}

	@Test
	void leapsADitchToTheFarSide() {
		assertEquals(2, GapJump.landingDistance(64, new int[] {60, 64, 64, 64}, ALL_SAFE));
		assertEquals(3, GapJump.landingDistance(64, new int[] {55, 55, 64, 64}, ALL_SAFE));
	}

	@Test
	void landsOnASlightlyHigherOrLowerBank() {
		assertEquals(2, GapJump.landingDistance(64, new int[] {58, 65, 65, 65}, ALL_SAFE));
		assertEquals(2, GapJump.landingDistance(64, new int[] {58, 63, 63, 63}, ALL_SAFE));
	}

	@Test
	void wontTryAGapTooWideOrAWallTooHigh() {
		assertEquals(0, GapJump.landingDistance(64, new int[] {50, 50, 50, 50}, ALL_SAFE));
		assertEquals(0, GapJump.landingDistance(64, new int[] {58, 67, 67, 67}, ALL_SAFE));
	}

	@Test
	void neverLandsInLavaOrFire() {
		assertEquals(0, GapJump.landingDistance(64, new int[] {58, 64, 58, 58}, new boolean[] {true, false, true, true}));
		assertEquals(3, GapJump.landingDistance(64, new int[] {58, 64, 64, 64}, new boolean[] {true, false, true, true}));
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
}
