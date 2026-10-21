package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class JourneyTest {
	private static final Vec3 HOME = new Vec3(0, 64, 0);
	private static final Vec3 FAR = new Vec3(1000, 70, 0);

	@Test
	void aimsOneStretchTowardsAFarDestination() {
		Vec3 aim = Journey.nextAim(HOME, FAR);
		assertEquals(Journey.STRETCH, aim.x, 1e-9);
		assertEquals(0, aim.z, 1e-9);
	}

	@Test
	void aimsStraightAtANearbyDestination() {
		Vec3 near = new Vec3(5, 70, 7);
		Vec3 aim = Journey.nextAim(HOME, near);
		assertEquals(5, aim.x, 1e-9);
		assertEquals(7, aim.z, 1e-9);
	}

	@Test
	void swervesEitherSideAtTheSameDistanceSmallestFirst() {
		List<Vec3> aims = Journey.aims(HOME, FAR);
		assertEquals(7, aims.size());
		for (Vec3 aim : aims)
			assertEquals(Journey.STRETCH, Journey.horizontalDistance(HOME, aim), 1e-6);
		assertEquals(aims.get(1).z, -aims.get(2).z, 1e-6);
		assertTrue(Math.abs(aims.get(1).z) < Math.abs(aims.get(3).z));
		for (Vec3 aim : aims)
			assertTrue(aim.x > 0, "never doubles back");
	}

	@Test
	void notStuckWhileMakingProgress() {
		Journey.Progress progress = new Journey.Progress();
		for (int i = 0; i < 100; i++)
			assertFalse(progress.isStuck(1000 - i * 10, i * 100L));
	}

	@Test
	void stuckAfterThirtySecondsWithoutGettingCloser() {
		Journey.Progress progress = new Journey.Progress();
		assertFalse(progress.isStuck(500, 0));
		assertFalse(progress.isStuck(498, Journey.Progress.PATIENCE - 1));
		assertTrue(progress.isStuck(498, Journey.Progress.PATIENCE));
	}

	@Test
	void givesUpAfterFiveTriesInARow() {
		Journey.Progress progress = new Journey.Progress();
		progress.isStuck(500, 0);
		for (int i = 0; i < Journey.Progress.MAX_TRIES; i++) {
			assertFalse(progress.outOfTries());
			progress.unstuck(500, i * 1000L);
		}
		assertTrue(progress.outOfTries());
	}

	@Test
	void realProgressStartsTheTriesAgain() {
		Journey.Progress progress = new Journey.Progress();
		progress.isStuck(500, 0);
		progress.unstuck(500, 100);
		progress.unstuck(500, 200);
		assertEquals(2, progress.triesInARow());
		progress.isStuck(400, 300);
		assertEquals(0, progress.triesInARow());
	}

	@Test
	void nightsSpentCampingDoNotCount() {
		Journey.Progress progress = new Journey.Progress();
		progress.isStuck(500, 0);
		progress.pause(10_000);
		assertFalse(progress.isStuck(500, 10_000 + Journey.Progress.PATIENCE - 1));
	}
}
