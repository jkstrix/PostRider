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
	void hisHorseDropsThreeBlocksButNotFourAsAnyFurtherHurtsThemBoth() {
		GridTerrain three = new GridTerrain().fill(-3, Y, -3, 3, Y + 2, 3, Kind.SOLID);
		LocalPlanner down = new LocalPlanner(three, 0, Y + 3, 0, true, LocalPlanner.Goal.anyHeight(20, 1, 1.5), true);
		assertEquals(Status.FOUND, down.finishNow());
		GridTerrain four = new GridTerrain().fill(-3, Y, -3, 3, Y + 3, 3, Kind.SOLID);
		LocalPlanner stuck = new LocalPlanner(four, 0, Y + 4, 0, true, LocalPlanner.Goal.anyHeight(20, 1, 1.5), false);
		assertEquals(Status.FAILED, stuck.finishNow());
		assertEquals(3, LocalPlanner.MOUNTED_DROP, "a fall of more than three blocks hurts, the rider as much as the horse");
		// On foot likewise
		assertEquals(Status.FAILED, new LocalPlanner(four, 0, Y + 4, 0, false, LocalPlanner.Goal.anyHeight(20, 1, 1.5), false).finishNow());
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

	/** A cliff ten blocks high from x 10 on, with a bubble column over soul sand up its face at x 9, open at the top. */
	private static GridTerrain cliffWithAColumnUp() {
		return new GridTerrain().fill(10, Y, -400, 60, Y + 9, 400, Kind.SOLID).fill(9, Y, 0, 9, Y + 9, 0, Kind.BUBBLE_UP);
	}

	/** A plateau ten blocks high up to x 10, with a whirlpool over magma down its face at x 11 and the low ground beyond. */
	private static GridTerrain plateauWithAWhirlpool() {
		return new GridTerrain().fill(-400, Y, -400, 10, Y + 9, 400, Kind.SOLID).fill(11, Y, 0, 11, Y + 9, 0, Kind.BUBBLE_DOWN)
			.fill(11, Y - 1, 0, 11, Y - 1, 0, Kind.DANGER);
	}

	@Test
	void onFootHeRidesABubbleColumnUpToTheLedgeAtItsTop() {
		LocalPlanner planner = walking(cliffWithAColumnUp(), 20.5, 0.5, false);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().filter(s -> s.move() == Move.RISE).count() >= 10, "into the column and up it: " + steps);
		assertEquals(Y + 10, steps.get(steps.size() - 1).y());
		for (Step step : steps)
			assertFalse(step.move() != Move.RISE && touches(cliffWithAColumnUp(), step, Kind.BUBBLE_UP, false), "in the water off the column: " + step);
	}

	@Test
	void onFootHeGoesDownAWhirlpoolAndStraightOffTheMagmaOntoTheLedgeBesideIt() {
		GridTerrain plateau = plateauWithAWhirlpool();
		LocalPlanner planner = new LocalPlanner(plateau, 0, Y + 10, 0, false, LocalPlanner.Goal.anyHeight(20.5, 0.5, 1.5), false);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().filter(s -> s.move() == Move.SINK).count() >= 10, "into the whirlpool and down it: " + steps);
		int last = -1;
		for (int i = 0; i < steps.size(); i++)
			if (steps.get(i).move() == Move.SINK)
				last = i;
		assertEquals(Y, steps.get(last).y(), "to the foot of it");
		Step off = steps.get(last + 1);
		assertEquals(Y, off.y(), "off the magma onto the ledge, not up");
		assertTrue(LocalPlanner.stands(plateau, false, off.x(), off.y(), off.z()), "onto something he can stand on: " + off);
		for (Step step : steps)
			assertFalse(step.move() != Move.SINK && touches(plateau, step, Kind.DANGER, true), "standing on the magma: " + step);
	}

	@Test
	void hisHorseCantUseABubbleColumnSoHeGetsDownFirst() {
		LocalPlanner planner = riding(cliffWithAColumnUp(), 20.5, 0.5);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertTrue(steps.stream().anyMatch(s -> s.move() == Move.DISMOUNT), steps.toString());
		for (Step step : steps)
			assertFalse(step.mounted() && touches(cliffWithAColumnUp(), step, Kind.BUBBLE_UP, false), "the horse in the column: " + step);
		assertTrue(steps.stream().anyMatch(s -> s.move() == Move.RISE));
	}

	@Test
	void aWhirlpoolWithNoLedgeAtItsFootIsNoWayDown() {
		// The foot of the whirlpool walled in on every side
		GridTerrain walled = plateauWithAWhirlpool().fill(12, Y, -1, 12, Y + 2, 1, Kind.SOLID).fill(10, Y, -1, 12, Y + 2, -1, Kind.SOLID)
			.fill(10, Y, 1, 12, Y + 2, 1, Kind.SOLID);
		LocalPlanner planner = new LocalPlanner(walled, 0, Y + 10, 0, false, LocalPlanner.Goal.anyHeight(20.5, 0.5, 1.5), false);
		assertNotEquals(Status.FOUND, planner.finishNow());
		for (Step step : planner.steps())
			assertNotEquals(Move.SINK, step.move(), "a way that ends in the water: " + step);
	}

	@Test
	void aBubbleColumnWithARoofOverItOnlyLetsHimOutLevelWithTheWater() {
		// A roof two blocks over the top of the column: no being thrown up onto the cliff top, but a ledge level with
		// the top of the water is still reached
		GridTerrain roofed = cliffWithAColumnUp().fill(9, Y + 11, 0, 9, Y + 11, 0, Kind.SOLID).fill(10, Y + 9, 0, 10, Y + 9, 0, Kind.OPEN)
			.fill(10, Y + 10, 0, 10, Y + 10, 0, Kind.OPEN).fill(11, Y + 9, 0, 11, Y + 10, 0, Kind.OPEN);
		LocalPlanner planner = walking(roofed, 11.5, 0.5, false);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		assertEquals(Y + 9, steps.get(steps.size() - 1).y(), "out level with the top of the water: " + steps);
	}

	@Test
	void aHorseIsNeverWalkedAlongHalfOverAnEdge() {
		GridTerrain ground = new GridTerrain();
		assertTrue(LocalPlanner.standsSquarely(ground, true, 10, Y, 10));
		// The two eastern columns of the footprint over a drop: it can land there (half a horse on the ground stands),
		// but it isn't walked along like that; on foot the western column is all he needs
		GridTerrain edge = new GridTerrain().fill(11, Y - 6, 0, 30, Y - 1, 30, Kind.OPEN);
		assertTrue(LocalPlanner.stands(edge, true, 10, Y, 10), "enough to land on");
		assertFalse(LocalPlanner.standsSquarely(edge, true, 10, Y, 10), "not enough to walk along");
		assertTrue(LocalPlanner.standsSquarely(edge, false, 10, Y, 10));
		assertTrue(LocalPlanner.standsSquarely(edge, true, 8, Y, 10), "a step back, all four feet on the ground");
		// A one-block step up: the back half of the horse over the lower tread is fine
		GridTerrain step = new GridTerrain().fill(11, Y, 0, 30, Y, 30, Kind.SOLID);
		assertTrue(LocalPlanner.standsSquarely(step, true, 10, Y + 1, 10), "stepping up with the back feet still on the lower tread");
	}

	@Test
	void aLeapNeverLandsAlongAOneBlockBridge() {
		// A chasm from x 12 to 15, bridged by a single line of blocks along z 10
		GridTerrain chasm = new GridTerrain(40).fill(12, Y - 6, -40, 15, Y - 1, 40, Kind.OPEN).fill(12, Y - 1, 10, 15, Y - 1, 10, Kind.SOLID);
		assertTrue(LocalPlanner.stands(chasm, true, 13, Y, 9), "half the horse is on the bridge");
		assertFalse(LocalPlanner.landsOn(chasm, 13, Y, 9, 1, 0), "but it can't land along it coming from the west");
		assertTrue(LocalPlanner.landsOn(chasm, 16, Y, 9, 1, 0), "the far bank, front feet on solid ground");
		LocalPlanner planner = riding(chasm, 25, 10);
		assertEquals(Status.FOUND, planner.step(100_000), "over by the bridge, on foot");
		for (Step step : planner.steps())
			if (step.x() >= 12 && step.x() <= 15)
				assertFalse(step.mounted(), "a horse on the one-block bridge at " + step);
	}

	@Test
	void heDoesntGetBackOnHisHorseOnAOneBlockBridge() {
		// A one-block causeway along z 10 from x 12 to 40 over a chasm, with open ground beyond
		GridTerrain causeway = new GridTerrain(120).fill(12, Y - 6, -120, 40, Y - 1, 120, Kind.OPEN).fill(12, Y - 1, 10, 40, Y - 1, 10, Kind.SOLID);
		// Far enough beyond it that riding the rest beats walking it
		LocalPlanner planner = walking(causeway, 100, 10, true);
		assertEquals(Status.FOUND, planner.step(200_000));
		boolean mounted = false;
		for (Step step : planner.steps()) {
			if (step.move() == Move.MOUNT)
				mounted = true;
			if (step.x() >= 12 && step.x() <= 40)
				assertFalse(step.mounted(), "on the causeway on horseback at " + step);
		}
		assertTrue(mounted, "and he does whistle it back once he's over");
	}

	/** A goal at a fixed height, as the mailbox is. */
	private static LocalPlanner toTheBox(Terrain terrain, int x, int y, int z, boolean mounted, double goalX, double goalZ, boolean mayMount) {
		return new LocalPlanner(terrain, x, y, z, mounted, new LocalPlanner.Goal(goalX, Y, goalZ, 3, 3), mayMount);
	}

	@Test
	void aMailboxInARoomIsFoundByTheRoomsOneWayOutHoweverFarRoundItIs() {
		// Two rooms side by side in solid rock, a wall between them, the goal in one and him in the other; the only way
		// from his to the goal's is a corridor that runs 60 blocks east, up a step, 10 north and 60 back: 130 blocks
		// of walking to a box 8 blocks away through the wall
		GridTerrain rock = new GridTerrain().fill(-10, Y, -10, 90, Y + 3, 30, Kind.SOLID)
			.fill(0, Y, 0, 4, Y + 2, 4, Kind.OPEN).fill(0, Y, 10, 4, Y + 2, 14, Kind.OPEN)
			.fill(5, Y, 2, 70, Y + 2, 2, Kind.OPEN).fill(70, Y, 2, 70, Y + 2, 12, Kind.OPEN).fill(5, Y, 12, 70, Y + 2, 12, Kind.OPEN);
		LocalPlanner planner = toTheBox(rock, 2, Y, 2, false, 2.5, 12.5, false);
		assertEquals(Status.FOUND, planner.finishNow(), "found from the goal's end, by the corridor");
		assertTrue(planner.expanded() < 2000, "without wandering: " + planner.expanded() + " places looked at");
		List<Step> steps = planner.steps();
		assertEquals(new Step(2, Y, 2, false, Move.START), steps.get(0));
		Step last = steps.get(steps.size() - 1);
		assertTrue(last.z() >= 10 && last.z() <= 14, "ends in the goal's room: " + last);
		assertTrue(steps.stream().anyMatch(st -> st.x() == 70), "by way of the corridor's far end");
		allStand(rock, steps);
		for (int i = 1; i < steps.size(); i++) {
			Step before = steps.get(i - 1), after = steps.get(i);
			assertTrue(Math.abs(before.x() - after.x()) <= 1 && Math.abs(before.z() - after.z()) <= 1, "one block at a time: " + before + " then " + after);
		}
	}

	@Test
	void theBackwardSearchAgreesWithTheForwardOneOnOpenGround() {
		GridTerrain flat = new GridTerrain();
		LocalPlanner backward = toTheBox(flat, 0, Y, 0, true, 30.5, 10.5, true);
		assertEquals(Status.FOUND, backward.finishNow());
		LocalPlanner forward = new LocalPlanner(flat, 0, Y, 0, true, LocalPlanner.Goal.anyHeight(30.5, 10.5, 3), true);
		assertEquals(Status.FOUND, forward.finishNow());
		assertEquals(forward.steps().size(), backward.steps().size(), 2, "as short a way either way");
		assertTrue(backward.steps().stream().allMatch(Step::mounted), "and ridden the whole way");
		allStand(flat, backward.steps());
	}

	@Test
	void aDoorInAWallIsGoneThroughOnFootRatherThanRiddenRound() {
		// A long wall, three high, with a door (both halves) in it straight ahead
		GridTerrain walled = new GridTerrain().fill(10, Y, -40, 10, Y + 2, 40, Kind.SOLID).fill(10, Y, 0, 10, Y + 1, 0, Kind.DOOR);
		LocalPlanner planner = riding(walled, 41, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		Step inTheDoor = steps.stream().filter(step -> step.x() == 10).findFirst().orElseThrow();
		assertFalse(inTheDoor.mounted(), "through the door on foot");
		assertEquals(0, inTheDoor.z(), "through the door, not the wall");
		assertTrue(steps.get(steps.size() - 1).mounted(), "back on his horse beyond it");
		allStand(walled, steps);
	}

	@Test
	void aHorseNeverGoesThroughADoorwayHoweverWideAndHighItIs() {
		// A double door, with headroom enough for a horse above it, in a long wall
		GridTerrain walled = new GridTerrain().fill(10, Y, -40, 10, Y + 3, 40, Kind.SOLID).fill(10, Y, 0, 10, Y + 1, 1, Kind.DOOR)
			.fill(10, Y + 2, 0, 10, Y + 2, 1, Kind.OPEN);
		assertFalse(LocalPlanner.stands(walled, true, 10, Y, 0), "a horse can't stand in a doorway");
		assertTrue(LocalPlanner.stands(walled, false, 10, Y, 0), "he can");
		LocalPlanner planner = riding(walled, 21, 1);
		assertEquals(Status.FOUND, planner.finishNow());
		assertTrue(planner.steps().stream().filter(step -> step.x() == 10).noneMatch(Step::mounted), "on foot through the doors");
	}

	@Test
	void onFootADeepHoleIsDroppedIntoStraightNeverCornerToCorner() {
		// A hole a block wide and three deep, diagonally ahead of him: the short way in is corner to corner
		GridTerrain pit = new GridTerrain().fill(3, Y - 3, 3, 3, Y - 1, 3, Kind.OPEN);
		LocalPlanner planner = new LocalPlanner(pit, 0, Y, 0, false, new LocalPlanner.Goal(3.5, Y - 3, 3.5, 0.6, 0), false);
		assertEquals(Status.FOUND, planner.finishNow());
		List<Step> steps = planner.steps();
		Step last = steps.get(steps.size() - 1), before = steps.get(steps.size() - 2);
		assertEquals(Y - 3, last.y(), "down in the hole");
		assertTrue(last.x() == before.x() || last.z() == before.z(), "dropped in straight, from " + before);
		allStand(pit, steps);
	}

	@Test
	void aVineThatStopsShortOfTheTopIsNoWayUp() {
		// The same cliff, with a vine up its face that ends a block below the top: from the top of the vine the ledge
		// is two up, and the air over the vine holds nobody (he'd fall through it), so there's no way up here
		GridTerrain cliff = new GridTerrain().fill(10, Y, -400, 60, Y + 9, 400, Kind.SOLID).fill(9, Y, 0, 9, Y + 8, 0, Kind.CLIMB);
		assertNotEquals(Status.FOUND, walking(cliff, 20.5, 0.5, false).finishNow());
		assertFalse(LocalPlanner.stands(cliff, false, 9, Y + 9, 0), "air over the top of a vine");
		assertTrue(LocalPlanner.stands(cliff, false, 9, Y + 8, 0), "in the vine, holding on");
	}
}
