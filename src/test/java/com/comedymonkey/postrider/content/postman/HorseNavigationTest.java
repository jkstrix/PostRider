package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/** Beside a hazard the horse has to come right up to each step of the way, where vanilla would let it turn 0.7 off. */
class HorseNavigationTest {
	private static final Vec3 STEP = new Vec3(42, 64, 32);

	@Test
	void aStepIsReachedOnlyCloseUpBesideAHazard() {
		assertTrue(HorseNavigation.reachedBesideHazard(new Vec3(41.8, 64, 32.2), STEP, 1));
		assertFalse(HorseNavigation.reachedBesideHazard(new Vec3(41.6, 64, 32), STEP, 1));
		assertFalse(HorseNavigation.reachedBesideHazard(new Vec3(42, 64, 32.4), STEP, 1));
	}

	@Test
	void notAtTheWrongHeight() {
		assertFalse(HorseNavigation.reachedBesideHazard(new Vec3(42, 65.5, 32), STEP, 1));
	}
}
