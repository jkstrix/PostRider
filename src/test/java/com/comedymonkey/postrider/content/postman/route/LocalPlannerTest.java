package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Move;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Status;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Step;
import com.comedymonkey.postrider.content.postman.route.Terrain.Kind;

class LocalPlannerTest {
	private static final int Y = GridTerrain.GROUND;

	private static LocalPlanner riding(Terrain terrain, double goalX, double goalZ) {
		return new LocalPlanner(terrain, 0, Y, 0, true, LocalPlanner.Goal.anyHeight(goalX, goalZ, 1.5), true);
	}

	private static LocalPlanner walking(Terrain terrain, double goalX, double goalZ, boolean mayMount) {
		return new LocalPlanner(terrain, 0, Y, 0, false, LocalPlanner.Goal.anyHeight(goalX, goalZ, 1.5), mayMount);
	}

	/** Every step after the first is somewhere he can really stand (leaps land somewhere he can stand too). */
	private static void allStand(Terrain terrain, List<Step> steps) {
		for (Step step : steps.subList(1, steps.size()))
			assertTrue(LocalPlanner.stands(terrain, step.mounted(), step.x(), step.y(), step.z()), step.toString());
	}

	/** Whether any block his body (or his horse's) is in at a step is of this kind; or, with {@code under}, below it. */
	private static boolean touches(Terrain terrain, Step step, Kind kind, boolean under) {
		int size = step.mounted() ? 2 : 1, height = step.mounted() ? 3 : 2;
		for (int dx = 0; dx < size; dx++)
			for (int dz = 0; dz < size; dz++)
				for (int dy = under ? -1 : 0; dy < height; dy++)
					if (terrain.kind(step.x() + dx, step.y() + dy, step.z() + dz) == kind)
						return true;
		return false;
	}

	@Test
	void onOpenGroundHeRidesStraightThere() {
		GridTerrain flat = new GridTerrain();
		LocalPlanner planner = riding(flat, 31, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().allMatch(Step::mounted));
		assertTrue(steps.size() <= 32, "no wandering: " + steps.size());
		allStand(flat, steps);
	}

	@Test
	void heGoesRoundAWall() {
		GridTerrain walled = new GridTerrain().fill(10, Y, -20, 10, Y + 2, 20, Kind.SOLID);
		LocalPlanner planner = riding(walled, 21, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		allStand(walled, steps);
		assertTrue(steps.stream().anyMatch(s -> Math.abs(s.z()) > 19), "round the end of it");
	}

	@Test
	void heNeverRidesThroughWater() {
		// A river five blocks wide right across his way, too wide to leap
		GridTerrain river = new GridTerrain().fill(10, Y - 1, -400, 14, Y - 1, 400, Kind.LIQUID);
		LocalPlanner planner = riding(river, 30, 1);
		assertNotEquals(Status.FOUND, planner.finishNow());
		for (Step step : planner.steps())
			assertFalse(touches(river, step, Kind.LIQUID, false), step.toString());
	}

	@Test
	void heCrossesTheRiverByTheBridge() {
		GridTerrain river = new GridTerrain().fill(10, Y - 1, -400, 14, Y - 1, 400, Kind.LIQUID)
			.fill(10, Y - 1, 20, 14, Y - 1, 22, Kind.SOLID);
		LocalPlanner planner = riding(river, 30, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		allStand(river, steps);
		for (Step step : steps)
			if (step.x() >= 10 && step.x() <= 13)
				assertTrue(step.z() + 1 >= 20 && step.z() <= 22, "over the river only on the bridge: " + step);
	}

	@Test
	void hisHorseCantStepUpTwoBlocksSoHeUsesTheRamp() {
		// A bank two blocks high all along, with a one-block step up to it at z 20
		GridTerrain bank = new GridTerrain().fill(10, Y, -400, 60, Y + 1, 400, Kind.SOLID)
			.fill(7, Y, 20, 9, Y, 23, Kind.SOLID);
		LocalPlanner planner = riding(bank, 30, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		allStand(bank, steps);
		assertTrue(steps.stream().anyMatch(s -> s.y() == Y + 1), "up the step");
		assertEquals(Y + 2, steps.get(steps.size() - 1).y());
	}

	@Test
	void hisHorseDropsFiveBlocksButNotSix() {
		GridTerrain five = new GridTerrain().fill(-3, Y, -3, 3, Y + 4, 3, Kind.SOLID);
		LocalPlanner down = new LocalPlanner(five, 0, Y + 5, 0, true, LocalPlanner.Goal.anyHeight(20, 1, 1.5), true);
		assertEquals(Status.FOUND, down.finishNow());
		GridTerrain six = new GridTerrain().fill(-3, Y, -3, 3, Y + 5, 3, Kind.SOLID);
		LocalPlanner stuck = new LocalPlanner(six, 0, Y + 6, 0, true, LocalPlanner.Goal.anyHeight(20, 1, 1.5), false);
		assertEquals(Status.FAILED, stuck.finishNow());
		// On foot he only jumps down three
		GridTerrain four = new GridTerrain().fill(-3, Y, -3, 3, Y + 3, 3, Kind.SOLID);
		assertEquals(Status.FAILED, new LocalPlanner(four, 0, Y + 4, 0, false, LocalPlanner.Goal.anyHeight(20, 1, 1.5), false).finishNow());
		GridTerrain three = new GridTerrain().fill(-3, Y, -3, 3, Y + 2, 3, Kind.SOLID);
		assertEquals(Status.FOUND, new LocalPlanner(three, 0, Y + 3, 0, false, LocalPlanner.Goal.anyHeight(20, 1, 1.5), false).finishNow());
	}

	@Test
	void hisHorseLeapsAThreeBlockGap() {
		GridTerrain ravine = new GridTerrain().fill(10, Y - 12, -400, 12, Y - 1, 400, Kind.OPEN);
		LocalPlanner planner = riding(ravine, 30, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().anyMatch(s -> s.move() == Move.LEAP));
		assertTrue(steps.stream().allMatch(s -> s.y() >= Y - 1), "never down in it");
		// On foot (and with no horse to call) there's no way over
		assertNotEquals(Status.FOUND, walking(ravine, 30, 0.5, false).finishNow());
	}

	@Test
	void hisHorseFitsDownATwoBlockWideLane() {
		GridTerrain lane = new GridTerrain().fill(5, Y, -1, 30, Y + 3, -1, Kind.SOLID).fill(5, Y, 2, 30, Y + 3, 2, Kind.SOLID)
			.fill(5, Y, -400, 5, Y + 3, -2, Kind.SOLID).fill(5, Y, 3, 5, Y + 3, 400, Kind.SOLID);
		LocalPlanner planner = riding(lane, 40, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		assertTrue(planner.steps().stream().allMatch(Step::mounted), "no need to get down");
	}

	@Test
	void aOneBlockGapMeansGettingDownAndWhistlingTheHorseBackAfter() {
		// A long high wall with a single one-block doorway in it
		GridTerrain wall = new GridTerrain().fill(15, Y, -400, 15, Y + 6, -1, Kind.SOLID).fill(15, Y, 1, 15, Y + 6, 400, Kind.SOLID);
		LocalPlanner planner = riding(wall, 90, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		allStand(wall, steps);
		int down = -1, up = -1;
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).move() == Move.DISMOUNT)
				down = i;
			if (steps.get(i).move() == Move.MOUNT)
				up = i;
		}
		assertTrue(down > 0 && up > down, "down, through, and back up");
		assertTrue(steps.stream().filter(s -> s.x() == 15).allMatch(s -> !s.mounted() && s.z() == 0), "through the doorway on foot");
		assertTrue(steps.get(steps.size() - 1).mounted());
	}

	@Test
	void onFootHeClimbsTheLadder() {
		// A cliff ten blocks high with a ladder up its face
		GridTerrain cliff = new GridTerrain().fill(10, Y, -400, 60, Y + 9, 400, Kind.SOLID).fill(9, Y, 0, 9, Y + 9, 0, Kind.CLIMB);
		LocalPlanner planner = walking(cliff, 20.5, 0.5, false);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().filter(s -> s.move() == Move.CLIMB).count() >= 8);
		assertEquals(Y + 10, steps.get(steps.size() - 1).y());
	}

	@Test
	void heRidesThroughATunnelRatherThanRoundTheMountain() {
		GridTerrain mountain = new GridTerrain().fill(10, Y, -300, 30, Y + 40, 300, Kind.SOLID).fill(10, Y, 0, 30, Y + 2, 1, Kind.OPEN);
		LocalPlanner planner = riding(mountain, 40, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		for (Step step : planner.steps()) {
			assertTrue(step.mounted(), "a three-high tunnel is room enough on horseback");
			if (step.x() >= 9 && step.x() <= 30)
				assertEquals(0, step.z(), "in the tunnel: " + step);
		}
	}

	@Test
	void heKeepsClearOfFireAndMagma() {
		GridTerrain hazards = new GridTerrain().fill(10, Y, -3, 10, Y, 3, Kind.DANGER).fill(16, Y - 1, -2, 17, Y - 1, 6, Kind.DANGER);
		LocalPlanner planner = riding(hazards, 30, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		for (Step step : planner.steps())
			assertFalse(touches(hazards, step, Kind.DANGER, true), step.toString());
	}

	@Test
	void unloadedGroundGetsHimAsCloseAsHeCan() {
		GridTerrain small = new GridTerrain(30);
		LocalPlanner planner = riding(small, 100, 1);
		assertEquals(Status.PARTIAL, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.get(steps.size() - 1).x() >= 25, "to the edge of what he can see");
	}

	@Test
	void workingItOutBitByBitGivesTheSameWay() {
		GridTerrain walled = new GridTerrain().fill(10, Y, -20, 10, Y + 2, 20, Kind.SOLID).fill(20, Y - 1, -5, 24, Y - 1, 30, Kind.LIQUID);
		LocalPlanner whole = riding(walled, 35, 1);
		whole.finishNow();
		LocalPlanner sliced = riding(walled, 35, 1);
		int slices = 0;
		while (sliced.step(7) == Status.RUNNING)
			slices++;
		assertTrue(slices > 10);
		assertEquals(whole.status(), sliced.status());
		assertEquals(whole.steps(), sliced.steps());
	}
}
