package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Move;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Step;

import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

class NavigatorTest {
	private static final List<Step> WAY = List.of(
		new Step(0, 64, 0, true, Move.START),
		new Step(1, 64, 0, true, Move.WALK),
		new Step(2, 64, 0, true, Move.WALK),
		new Step(2, 64, 0, false, Move.DISMOUNT),
		new Step(3, 64, 0, false, Move.WALK),
		new Step(3, 65, 0, false, Move.CLIMB),
		new Step(2, 65, 0, true, Move.MOUNT),
		new Step(3, 65, 0, true, Move.WALK));

	@Test
	void aHorsesStepIsTheCornerOfTheBlocksItStandsOver() {
		// As vanilla paths have it for anything wider than a block: the horse's middle is at the corner, his in the block
		Step riding = new Step(10, 64, 20, true, Move.WALK), walking = new Step(10, 64, 20, false, Move.WALK);
		assertEquals(11.0, riding.centreX());
		assertEquals(21.0, riding.centreZ());
		assertEquals(10.5, walking.centreX());
		assertEquals(20.5, walking.centreZ());
	}

	@Test
	void theWayIsHandedOverAPieceAtATimeWhereverHeGetsOnOrOff() {
		assertEquals(2, Navigator.segmentEnd(WAY, 0));
		assertEquals(5, Navigator.segmentEnd(WAY, 3));
		assertEquals(7, Navigator.segmentEnd(WAY, 6));
		Path path = Navigator.toPath(WAY, 3, 5, false);
		assertEquals(3, path.getNodeCount());
		assertEquals(3, path.getNode(1).x);
		assertEquals(65, path.getNode(2).y);
		assertEquals(PathType.WALKABLE, path.getNode(0).type);
		assertFalse(path.canReach());
		assertTrue(Navigator.toPath(WAY, 6, 7, true).canReach());
	}

	@Test
	void heDoesntTurnBackForAStepHesAlreadyPast() {
		List<Step> line = List.of(new Step(0, 64, 0, false, Move.START), new Step(1, 64, 0, false, Move.WALK),
			new Step(2, 64, 0, false, Move.WALK), new Step(3, 64, 0, false, Move.WALK));
		assertEquals(0, Navigator.firstToHeadFor(line, 0, 3, 0.2, 64, 0.5));
		assertEquals(2, Navigator.firstToHeadFor(line, 0, 3, 1.9, 64, 0.5), "between the second and third: on to the third");
		assertEquals(0, Navigator.firstToHeadFor(line, 0, 3, 1.9, 70, 0.5), "six blocks above the way: none of its steps is near, so the first");
	}

	@Test
	void theNextAimIsAStretchOnAlongTheLeg() {
		List<Vec3> leg = List.of(new Vec3(0, 64, 0), new Vec3(100, 64, 0), new Vec3(100, 64, 100));
		Vec3 aim = Navigator.along(leg, new Vec3(10, 64, 5), 40);
		assertEquals(50, aim.x, 1e-6);
		assertEquals(0, aim.z, 1e-6);
		Vec3 round = Navigator.along(leg, new Vec3(90, 64, 0), 30);
		assertEquals(100, round.x, 1e-6);
		assertEquals(20, round.z, 1e-6, "round the corner");
		assertEquals(new Vec3(100, 64, 100), Navigator.along(leg, new Vec3(100, 64, 90), 40), "no further than the end");
	}

	private static final List<CoarsePlanner.Leg> TO_THE_RIVER = List.of(
		new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(0, 64, 0), new Vec3(40, 64, 0))),
		new CoarsePlanner.Leg(CoarsePlanner.LegKind.WATER, List.of(new Vec3(44, 62, 0), new Vec3(60, 62, 0))),
		new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(64, 64, 0), new Vec3(120, 64, 0))));

	@Test
	void onFootWithTheRiverJustAheadHeWalksDownToIt() {
		assertTrue(Navigator.launchWithin(TO_THE_RIVER, 0, new Vec3(30, 64, 0), Navigator.FOOT_TO_WATER), "10 blocks from the water");
		assertFalse(Navigator.launchWithin(TO_THE_RIVER, 0, new Vec3(5, 64, 0), Navigator.FOOT_TO_WATER), "a long way off: he calls his horse");
		assertTrue(Navigator.launchWithin(TO_THE_RIVER, 1, new Vec3(50, 62, 0), Navigator.FOOT_TO_WATER), "on the water already");
		assertFalse(Navigator.launchWithin(TO_THE_RIVER, 2, new Vec3(70, 64, 0), Navigator.FOOT_TO_WATER), "over it, no more water ahead");
	}

	@Test
	void atTheEndOfTheWaterHeRunsTheBowUpOntoTheBank() {
		List<Vec3> across = List.of(new Vec3(0.5, 0, 0.5), new Vec3(5.5, 0, 0.5), new Vec3(10.5, 0, 0.5));
		Vec3 bank = new Vec3(14.5, 64, 0.5);
		// Mid-way he steers a few blocks along the way
		assertEquals(0.5 + Navigator.WATER_LOOKAHEAD, Navigator.steerFor(across, true, bank, new Vec3(0.5, 62, 0.5)).x, 1e-6);
		// Near its end he steers for the bank itself, rather than drifting to a stop short of it
		assertEquals(bank, Navigator.steerFor(across, true, bank, new Vec3(8.5, 62, 0.5)));
		// Not where he lands (just a stretch of a long crossing): on along the way
		assertTrue(Navigator.steerFor(across, false, bank, new Vec3(8.5, 62, 0.5)).x <= 10.5);
	}

	@Test
	void waterHeCantPlanPastMeansTheBargeEvenIfTheLongRangePlanCalledItLand() {
		// A river narrower than the long-range plan's cells: no way through the blocks, open water ahead
		assertEquals(Navigator.Failed.BARGE, Navigator.whenStretchFails(false, true, true, false, false, 0));
		assertEquals(Navigator.Failed.BARGE, Navigator.whenStretchFails(true, true, true, false, false, 0), "even with the mailbox over the water");
		// No water: the plan is redone round whatever's in the way
		assertEquals(Navigator.Failed.REPLAN, Navigator.whenStretchFails(false, false, true, false, false, 0));
		assertEquals(Navigator.Failed.DROP_PLAN, Navigator.whenStretchFails(false, false, true, false, false, Navigator.MAX_BLOCKED));
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(true, false, true, false, false, 0), "can't reach the mailbox itself: getting unstuck sorts it");
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(false, false, false, false, false, 0));
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(false, false, true, true, false, 0), "down at the water's edge, nothing to mark blocked");
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(false, false, true, false, true, 0), "a new route is still being worked out: not started over");
		assertEquals(Navigator.Failed.BARGE, Navigator.whenStretchFails(false, true, true, false, true, 0), "but water in the way is still the barge");
	}

	@Test
	void theLogSaysWhatRouteHePlannedInAFewWords() {
		assertEquals("land 40 blocks, then barge 16 blocks, then land 56 blocks", Navigator.describe(new CoarsePlanner.Plan(TO_THE_RIVER, 0)));
		assertEquals("12 64 -3", Navigator.spot(new Vec3(12.7, 64.2, -2.5)));
	}

	@Test
	void headingForTheWaterHeMakesForTheBank() {
		// Water from x 34 on; the middle of the first water cell is out at x 38
		Navigator.Wet lake = (x, z) -> x >= 34;
		Vec3 bank = Navigator.shore(new Vec3(7.5, 64, 32.5), new Vec3(38, 62, 32), lake);
		assertEquals(33.5, bank.x, 1e-9, "the last dry block before the water");
		assertEquals(32.5, bank.z, 1e-9);
		assertEquals(new Vec3(38, 62, 32), Navigator.shore(new Vec3(7.5, 64, 32.5), new Vec3(38, 62, 32), (x, z) -> false), "all dry: on to the point");
	}

	@Test
	void aNewPlanIsPickedUpWhereHeIsNotWhereHeWas() {
		// TO_THE_RIVER: land from x 0 to 40, barge 44 to 60, land 64 to 120
		assertEquals(0, Navigator.startingLeg(TO_THE_RIVER, new Vec3(10, 64, 0), false), "still riding down to the river");
		assertEquals(1, Navigator.startingLeg(TO_THE_RIVER, new Vec3(50, 62, 0), false), "out on the water");
		assertEquals(2, Navigator.startingLeg(TO_THE_RIVER, new Vec3(90, 64, 0), false), "chunks past the landing: on, not back to the water");
		// Planned afloat, finished after he landed: he never turns back to row the water it starts with
		List<CoarsePlanner.Leg> fromTheWater = List.of(
			new CoarsePlanner.Leg(CoarsePlanner.LegKind.WATER, List.of(new Vec3(0, 62, 0), new Vec3(8, 62, 0))),
			new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(12, 64, 0), new Vec3(80, 64, 0))));
		assertEquals(1, Navigator.startingLeg(fromTheWater, new Vec3(9, 64, 0), true));
		assertEquals(0, Navigator.startingLeg(fromTheWater, new Vec3(2, 62, 0), false), "still afloat: the water first");
	}

	@Test
	void ifHisHorseGivesUpFollowingTheWayHePlansItAgainRatherThanStandThere() {
		assertTrue(Navigator.stalledShort(false, 20, 60), "a stretch on the way");
		assertTrue(Navigator.stalledShort(true, 20, 24), "the last stretch, still well off the mailbox");
		assertFalse(Navigator.stalledShort(true, 2, 3), "at the mailbox: nothing to plan");
		assertTrue(Navigator.stalledShort(true, 3, 6.5), "at the end of the way to the box but still too far from it to walk up: planned again");
	}

	@Test
	void aShoreHeHasToPutOutFromAgainSoonAfterLandingIsNotLandedOnAgain() {
		Vec3 landing = new Vec3(100, 64, 100);
		assertTrue(Navigator.landedBackSoon(landing, 1000, new Vec3(110, 64, 95), 1400), "back on the water within a minute, close by");
		assertFalse(Navigator.landedBackSoon(landing, 1000, new Vec3(110, 64, 95), 1000 + Navigator.LANDED_BACK_TICKS + 1), "a while later");
		assertFalse(Navigator.landedBackSoon(landing, 1000, new Vec3(200, 64, 100), 1400), "somewhere else altogether");
		// The shore round where he landed is marked, but not the water (it's what he launches from)
		CoarsePlanner.Cell land = new CoarsePlanner.Cell(64, 0, 0, false), water = new CoarsePlanner.Cell(62, 4, 0, false);
		int lx = CoarsePlanner.cellOf(100), lz = CoarsePlanner.cellOf(100);
		List<Long> shore = Navigator.shoreAround(landing, (cx, cz) -> cx < lx ? water : land);
		assertEquals(6, shore.size());
		assertTrue(shore.contains(CoarsePlanner.key(lx, lz)));
		assertFalse(shore.contains(CoarsePlanner.key(lx - 1, lz)), "the water next to it stays open");
	}

	@Test
	void laddersAndBubbleColumnsAreWhatHeIsSetSquareInto() {
		assertTrue(Navigator.climbable(Terrain.Kind.CLIMB));
		assertTrue(Navigator.climbable(Terrain.Kind.BUBBLE_UP));
		assertTrue(Navigator.climbable(Terrain.Kind.BUBBLE_DOWN));
		assertFalse(Navigator.climbable(Terrain.Kind.OPEN));
		assertTrue(Navigator.CLIMB_NUDGE < 2, "only from right beside it");
	}

	@Test
	void aLadderIsARunOfColumnStepsInOneColumn() {
		List<Step> shaft = List.of(
			new Step(5, 70, 5, false, Move.WALK),
			new Step(6, 70, 5, false, Move.WALK),
			new Step(6, 69, 5, false, Move.CLIMB),
			new Step(6, 68, 5, false, Move.CLIMB),
			new Step(6, 67, 5, false, Move.CLIMB),
			new Step(7, 67, 5, false, Move.WALK),
			new Step(7, 66, 5, false, Move.SINK));
		assertEquals(4, Navigator.columnRunEnd(shaft, 2), "down the three ladder steps");
		assertEquals(1, Navigator.columnRunEnd(shaft, 1), "a walk isn't a column");
		assertEquals(6, Navigator.columnRunEnd(shaft, 6), "a whirlpool on its own");
		assertTrue(Navigator.isColumn(Move.RISE));
		assertFalse(Navigator.isColumn(Move.LEAP));
	}

	@Test
	void outOfTheTopOfARisingColumnHesPushedTowardsTheLedgeAndNoFurther() {
		Step ledge = new Step(65, 13, 45, false, Move.WALK);
		Vec3 push = Navigator.pushOut(66.5, 45.5, ledge);
		assertEquals(-Navigator.PUSH_OUT, push.x, 1e-9, "west, onto the ledge");
		assertEquals(0, push.z, 1e-9);
		assertEquals(0, push.y, 1e-9, "the water does the lifting");
		Vec3 nearly = Navigator.pushOut(65.6, 45.5, ledge);
		assertEquals(-0.1, nearly.x, 1e-9, "only as far as the middle of the ledge");
		assertEquals(Vec3.ZERO, Navigator.pushOut(65.5, 45.5, ledge));
	}

	@Test
	void aStepIsOnlyPassedFromBesideItOnItsOwnLevel() {
		// The way runs east along z 0.5 at y 64, from step a (0.5) to step b (1.5)
		assertTrue(Navigator.passed(1.2, 64, 0.5, 0.5, 64, 0.5, 1.5, 0.5), "between a and b, nearer b");
		assertFalse(Navigator.passed(0.3, 64, 0.5, 0.5, 64, 0.5, 1.5, 0.5), "not yet at a");
		assertFalse(Navigator.passed(1.2, 69, 0.5, 0.5, 64, 0.5, 1.5, 0.5), "on the ground over the tunnel the way runs through");
		assertFalse(Navigator.passed(1.2, 64, 4.5, 0.5, 64, 0.5, 1.5, 0.5), "four blocks off to the side of the way");
		assertFalse(Navigator.passed(20, 64, 0.5, 0.5, 64, 0.5, 1.5, 0.5), "far beyond b");
	}

	@Test
	void onlyAPlanThatReachesTheBoxEndsInAStretchAimedAtIt() {
		Vec3 box = new Vec3(148.5, 100, -68.5);
		assertTrue(Navigator.endsAtTheBox(new Vec3(140, 100, -60), box, 40), "the plan's end is a stretch from the box");
		assertFalse(Navigator.endsAtTheBox(new Vec3(1105, 87, 4451), box, 40), "the best plan so far ended thousands of blocks short: not the box");
	}

	@Test
	void aScrapOfShoreThatLedNowhereIsKeptClearOfByTheBlockNotTheCell() {
		List<Vec3> noLandings = List.of(new Vec3(-1032.5, 63, 705.5));
		assertTrue(Navigator.nearNoLanding(noLandings, -1032, 705), "the scrap itself");
		assertTrue(Navigator.nearNoLanding(noLandings, -1024, 710), "the beach a few blocks along");
		assertFalse(Navigator.nearNoLanding(noLandings, -1000, 705), "the coast well on from it");
		assertFalse(Navigator.nearNoLanding(List.of(), -1032, 705));
	}

	@Test
	void theWayAheadGoingDownIsSeenAStepOrTwoBeforeIt() {
		List<Step> down = List.of(
			new Step(0, 70, 0, true, Move.START),
			new Step(1, 70, 0, true, Move.WALK),
			new Step(2, 70, 0, true, Move.WALK),
			new Step(3, 69, 0, true, Move.WALK),
			new Step(4, 68, 0, true, Move.WALK),
			new Step(5, 69, 0, true, Move.WALK));
		assertTrue(Navigator.descends(down, 1, 1 + Navigator.DESCENT_LOOKAHEAD, 70), "the drop at the fourth step is within the look-ahead");
		assertFalse(Navigator.descends(down, 0, 2, 70), "level so far");
		assertFalse(Navigator.descends(down, 5, 5, 68), "a step up isn't a descent");
		assertTrue(Navigator.descends(down, 3, 3, 70), "the first step against where he stands");
	}

	@Test
	void aLongTripIsPlannedAStageAtATime() {
		Vec3 from = new Vec3(0, 64, 0), target = new Vec3(3000, 64, 0);
		Vec3 stage = Navigator.stagePoint(from, target, Navigator.STAGE);
		assertEquals(Navigator.STAGE, stage.x, 1e-9, "a stage along the straight line");
		assertEquals(0, stage.z, 1e-9);
		assertEquals(new Vec3(500, 64, 0), Navigator.stagePoint(from, new Vec3(500, 64, 0), Navigator.STAGE), "a shorter trip is planned whole");
		// The stage point is in a lake: the nearest land cell is made for instead
		CoarsePlanner.Cells lake = (cx, cz) -> cx >= 124 && cx <= 126 && cz >= -1 && cz <= 1 ? new CoarsePlanner.Cell(62, 4, 0, false) : new CoarsePlanner.Cell(64, 0, 0, false);
		Vec3 land = Navigator.nearestLand(lake, stage);
		assertTrue(CoarsePlanner.cellOf(land.x) < 124 || CoarsePlanner.cellOf(land.x) > 126 || Math.abs(CoarsePlanner.cellOf(land.z)) > 1, "out of the lake: " + land);
		assertTrue(Journey.horizontalDistance(land, stage) <= 2 * CoarsePlanner.CELL * 1.5, "but close by it");
	}

	@Test
	void aPlanNoLongerFittingTheGroundIsNoticed() {
		CoarsePlanner.Leg land = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(92, 64, 4)));
		CoarsePlanner.Leg water = new CoarsePlanner.Leg(CoarsePlanner.LegKind.WATER, List.of(new Vec3(100, 62, 4), new Vec3(140, 62, 4)));
		CoarsePlanner.Cells asPlanned = (cx, cz) -> cx >= 12 ? new CoarsePlanner.Cell(62, 4, 0, false) : new CoarsePlanner.Cell(64, 0, 0, false);
		assertTrue(Navigator.legsFit(List.of(land, water), 0, asPlanned));
		// A lake right across the leg, wider than a cell in every direction (a single water cell beside the line is a corner clipped, not a lake)
		CoarsePlanner.Cells lakeTurnedUp = (cx, cz) -> cx >= 5 && cx <= 7 || cx >= 12 ? new CoarsePlanner.Cell(62, 4, 0, false) : new CoarsePlanner.Cell(64, 0, 0, false);
		assertFalse(Navigator.legsFit(List.of(land, water), 0, lakeTurnedUp), "water across the land leg");
		CoarsePlanner.Cells unknown = (cx, cz) -> null;
		assertTrue(Navigator.legsFit(List.of(land, water), 0, unknown), "what isn't known yet is trusted");
		assertTrue(Navigator.legsFit(List.of(land, water), 1, lakeTurnedUp), "the leg he's past doesn't matter");
	}

	@Test
	void theChunksAheadFollowThePlanForAFewHundredBlocks() {
		CoarsePlanner.Leg leg = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(804, 64, 4)));
		List<Vec3> points = Navigator.pointsAhead(List.of(leg), 0, new Vec3(100, 64, 4), Navigator.AHEAD_BLOCKS, CoarsePlanner.CELL);
		assertEquals(Navigator.AHEAD_BLOCKS / CoarsePlanner.CELL + 1, points.size(), "a point every cell along");
		assertEquals(100, points.get(0).x, 1e-9, "from where he is");
		assertEquals(100 + Navigator.AHEAD_BLOCKS, points.get(points.size() - 1).x, 1e-9, "to the window's end");
		List<Vec3> nearEnd = Navigator.pointsAhead(List.of(leg), 0, new Vec3(790, 64, 4), Navigator.AHEAD_BLOCKS, CoarsePlanner.CELL);
		assertTrue(nearEnd.size() < 5, "the plan ends sooner: " + nearEnd.size());
	}

	@Test
	void theCellsFromHereToTheGoalAreTheOnesStruckOff() {
		List<Long> cells = Navigator.cellsBetween(new Vec3(4, 64, 4), new Vec3(44, 64, 4));
		assertEquals(8 * 3, cells.size(), "his own cell, four between, and the goal's, the cells either side of each, and one beyond each end");
		assertTrue(cells.contains(CoarsePlanner.key(0, 0)));
		assertTrue(cells.contains(CoarsePlanner.key(5, 0)));
		assertTrue(cells.contains(CoarsePlanner.key(3, 1)) && cells.contains(CoarsePlanner.key(3, -1)), "a band, not a thread");
		assertEquals(9, Navigator.cellsBetween(new Vec3(4, 64, 4), new Vec3(5, 64, 5)).size(), "the same cell once, with its neighbours");
	}

	@Test
	void aLandLegOverACliffNoLongerFits() {
		CoarsePlanner.Leg land = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(92, 64, 4)));
		CoarsePlanner.Cells gentle = (cx, cz) -> new CoarsePlanner.Cell(64 + cx * 3, 0, 3, false);
		assertTrue(Navigator.legsFit(List.of(land), 0, gentle), "three up a cell, rolling: ridden");
		CoarsePlanner.Cells cliffWithin = (cx, cz) -> new CoarsePlanner.Cell(64, 0, cx >= 5 && cx <= 7 ? 12 : 0, false);
		assertFalse(Navigator.legsFit(List.of(land), 0, cliffWithin), "a twelve-block cliff inside the cells on the way");
		CoarsePlanner.Cells cliffAtTheEnds = (cx, cz) -> new CoarsePlanner.Cell(64, 0, cx == 0 || cx == 11 ? 12 : 0, false);
		assertTrue(Navigator.legsFit(List.of(land), 0, cliffAtTheEnds), "where he stands and the mailbox's cell may be as steep as they like");
		assertEquals(CoarsePlanner.MAX_RISE, Navigator.STEEP_BETWEEN, "the check asks no more of a step than the plan did, or it'd never pass");
		assertEquals(CoarsePlanner.CLIFF_WITHIN, Navigator.STEEP_WITHIN, "nor of a cell");
		CoarsePlanner.Cells stepBetween = (cx, cz) -> new CoarsePlanner.Cell(cx >= 6 ? 80 : 64, 0, 0, false);
		assertFalse(Navigator.legsFit(List.of(land), 0, stepBetween), "sixteen up from one cell to the next");
		CoarsePlanner.Cells terrace = (cx, cz) -> new CoarsePlanner.Cell(cx >= 6 ? 70 : 64, 0, 0, false);
		assertTrue(Navigator.legsFit(List.of(land), 0, terrace), "six up from one cell to the next is a slope");
	}

	@Test
	void theMisfitSaysWhatAndWhere() {
		CoarsePlanner.Leg land = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(92, 64, 4)));
		CoarsePlanner.Cells lake = (cx, cz) -> new CoarsePlanner.Cell(64, cx >= 4 && cx <= 7 ? 4 : 0, 0, false);
		String misfit = Navigator.misfit(List.of(land), 0, lake, null, Double.MAX_VALUE);
		assertNotNull(misfit);
		assertTrue(misfit.startsWith("land leg 1/1 over water at "), misfit);
		CoarsePlanner.Cells stepBetween = (cx, cz) -> new CoarsePlanner.Cell(cx >= 6 ? 80 : 64, 0, 0, false);
		misfit = Navigator.misfit(List.of(land), 0, stepBetween, null, Double.MAX_VALUE);
		assertNotNull(misfit);
		assertTrue(misfit.contains("a step of 16"), misfit);
		assertNull(Navigator.misfit(List.of(land), 0, (cx, cz) -> new CoarsePlanner.Cell(64, 0, 0, false), null, Double.MAX_VALUE));
	}

	@Test
	void progressIsMeasuredAlongTheRouteNotAsTheCrowFlies() {
		// A route that goes east then south round a hill, to a box at its end
		CoarsePlanner.Leg round = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(0, 64, 0), new Vec3(100, 64, 0), new Vec3(100, 64, 100)));
		Vec3 box = new Vec3(100, 64, 100);
		double atTheStart = Navigator.alongToGo(List.of(round), 0, new Vec3(0, 64, 0), box);
		assertEquals(200, atTheStart, 1e-6);
		double halfwayAlongTheFirstLeg = Navigator.alongToGo(List.of(round), 0, new Vec3(50, 64, 5), box);
		assertEquals(5 + 50 + 100, halfwayAlongTheFirstLeg, 1e-6, "five blocks off the line, then along it to the end");
		double nearTheCorner = Navigator.alongToGo(List.of(round), 0, new Vec3(90, 64, 0), box);
		assertTrue(nearTheCorner < halfwayAlongTheFirstLeg, "heading east is progress though the box is south-east");
		assertEquals(Journey.horizontalDistance(new Vec3(0, 64, 0), box), Navigator.alongToGo(List.of(), 0, new Vec3(0, 64, 0), box), 1e-6, "no plan: straight there");
		assertEquals(0, Navigator.alongToGo(List.of(round), 0, box, box), 1e-6);
	}

	@Test
	void heNeverGetsDownFromAHorseInTheAir() {
		assertTrue(Navigator.settledToDismount(true, -0.078), "standing on the ground (resting fall speed)");
		assertFalse(Navigator.settledToDismount(false, -0.5), "mid-leap, falling: got off here he'd fall into the gap");
		assertFalse(Navigator.settledToDismount(false, 0.6), "just taken off");
		assertFalse(Navigator.settledToDismount(true, 0.6), "the tick the horse pushes off");
	}

	@Test
	void aStretchThatFallsShortStopsBeforeADropHeCouldntClimbBackUp() {
		List<Step> steps = List.of(new Step(0, 64, 0, false, Move.START), new Step(0, 64, 1, false, Move.WALK), new Step(0, 63, 2, false, Move.WALK),
			new Step(0, 61, 3, false, Move.WALK), new Step(0, 60, 4, false, Move.WALK));
		List<Step> taken = Navigator.upToTheFirstDrop(steps);
		assertEquals(3, taken.size(), "a block down is climbed back; two down isn't");
		assertEquals(63, taken.get(2).y());
		assertEquals(steps.subList(0, 3), Navigator.upToTheFirstDrop(steps.subList(0, 3)), "no big drop: the whole stretch");
		assertEquals(1, Navigator.upToTheFirstDrop(List.of(new Step(0, 64, 0, false, Move.START), new Step(0, 61, 1, false, Move.WALK))).size(), "nothing safe in it");
	}

	@Test
	void heClimbsToTheTopOfTheLastRungWhenTheWayGoesOnUpOntoALedge() {
		List<Step> ladderToLedge = List.of(new Step(0, 64, 0, false, Move.START), new Step(0, 65, 0, false, Move.CLIMB), new Step(0, 66, 0, false, Move.CLIMB),
			new Step(0, 67, 1, false, Move.WALK));
		assertEquals(67, Navigator.columnTop(ladderToLedge, 2), "the ledge is a block above the last rung: up to the top of it");
		List<Step> ladderToLevel = List.of(new Step(0, 64, 0, false, Move.START), new Step(0, 65, 0, false, Move.CLIMB), new Step(0, 66, 0, false, Move.CLIMB),
			new Step(0, 66, 1, false, Move.WALK));
		assertEquals(66, Navigator.columnTop(ladderToLevel, 2), "stepping off level with the rung");
		assertEquals(66, Navigator.columnTop(ladderToLevel.subList(0, 3), 2), "the column is the end of the way");
	}

	@Test
	void onTheLipOfADropHeIsNudgedOff() {
		assertTrue(Navigator.onTheLip(112, true, 110, 0.3), "two down, right under him");
		assertFalse(Navigator.onTheLip(112, true, 111, 0.3), "a block down is just walked off");
		assertFalse(Navigator.onTheLip(112, true, 110, 1.5), "still some way to walk to the edge");
		assertFalse(Navigator.onTheLip(112, false, 110, 0.3), "already in the air");
	}

	@Test
	void noStretchFollowsAPlanThatsBeenStruckOffWhileTheNewOneIsWorkedOut() {
		assertFalse(Navigator.mayPlanStretch(true, true), "struck off, and the new route still being worked out: he waits");
		assertTrue(Navigator.mayPlanStretch(true, false), "the new route is in (or there isn't going to be one)");
		assertTrue(Navigator.mayPlanStretch(false, true), "a route being redone for any other reason: he keeps to the way he's on");
		assertTrue(Navigator.REPLAN_PATIENCE >= 20 * 30 && Navigator.REPLAN_PATIENCE <= 20 * 120, "a route redone is waited for about a minute, then he goes by the best of it");
	}

	@Test
	void windingUpAStairToABoxOnATowerCountsAsGettingOn() {
		Vec3 boxOnTheTower = new Vec3(32, 314, 32);
		CoarsePlanner.Leg toTheFoot = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(32, 64, 32)));
		double atTheFoot = Navigator.alongToGo(List.of(toTheFoot), 0, new Vec3(32, 64, 32), boxOnTheTower);
		double halfwayUp = Navigator.alongToGo(List.of(toTheFoot), 0, new Vec3(30, 189, 34), boxOnTheTower);
		assertTrue(halfwayUp < atTheFoot - 50, "no nearer across, but a long way up (half of it counts): " + atTheFoot + " to " + halfwayUp);
		assertEquals(0, Navigator.alongToGo(List.of(toTheFoot), 0, boxOnTheTower, boxOnTheTower), 1e-6);
	}

	@Test
	void theStrikeNeverEnclosesWhereHeStands() {
		Vec3 here = new Vec3(4, 64, 4), goal = new Vec3(44, 64, 4);
		List<Long> band = Navigator.strikeBand(here, goal);
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				assertFalse(band.contains(CoarsePlanner.key(dx, dz)), "his own cell and the eight round it are never struck: " + dx + " " + dz);
		assertTrue(band.contains(CoarsePlanner.key(5, 0)), "the goal's cell is");
		assertTrue(band.contains(CoarsePlanner.key(3, 1)), "and the band either side of the line");
		List<Long> nextDoor = Navigator.strikeBand(here, new Vec3(12, 64, 4));
		assertFalse(nextDoor.contains(CoarsePlanner.key(1, 0)), "a goal in the next cell: that cell is kept");
		assertTrue(nextDoor.contains(CoarsePlanner.key(2, 0)), "the cells beyond it are struck");
	}

	@Test
	void aStagePointThatTurnsOutWetIsNoMisfit() {
		CoarsePlanner.Leg land = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 64, 4), new Vec3(92, 64, 4)));
		CoarsePlanner.Cells lakeAtTheEnd = (cx, cz) -> new CoarsePlanner.Cell(64, cx >= 10 ? 4 : 0, 0, false);
		assertNotNull(Navigator.misfit(List.of(land), 0, lakeAtTheEnd, null, Double.MAX_VALUE, false), "the mailbox's cell under water: a misfit");
		assertNull(Navigator.misfit(List.of(land), 0, lakeAtTheEnd, null, Double.MAX_VALUE, true), "a stage point under water: he'll arrive by barge");
		CoarsePlanner.Cells lakeOnTheWay = (cx, cz) -> new CoarsePlanner.Cell(64, cx >= 5 && cx <= 7 ? 4 : 0, 0, false);
		assertNotNull(Navigator.misfit(List.of(land), 0, lakeOnTheWay, null, Double.MAX_VALUE, true), "water on the way is still a misfit");
	}

	@Test
	void heIsAshoreAMomentBeforePuttingOutAgain() {
		assertFalse(Navigator.ashoreLongEnough(1005, 1000), "the tick after stepping ashore");
		assertTrue(Navigator.ashoreLongEnough(1000 + Navigator.ASHORE_TICKS, 1000), "a second ashore");
		assertTrue(Navigator.ashoreLongEnough(5000, 0), "never landed this trip: the launch is from the start");
	}

	@Test
	void aStepIsSkippedOnlyIfTheNextIsAtHisLevel() {
		assertTrue(Navigator.mayPassOnTo(87.0, 88), "a block up: the next step is a step he can take");
		assertFalse(Navigator.mayPassOnTo(87.0, 89), "two up: he must climb to the step between first");
		assertFalse(Navigator.mayPassOnTo(87.0, 83), "four down: the step between is the landing he mustn't miss");
		assertTrue(Navigator.mayPassOnTo(87.0, 87));
	}

	@Test
	void boxedInAfloatByHisOwnStrikesTheShoresAreForgivenToo() {
		java.util.Set<Long> blocked = new java.util.HashSet<>(java.util.List.of(1L, 2L, 3L));
		java.util.Set<Long> noShore = new java.util.HashSet<>(java.util.List.of(2L, 3L));
		java.util.List<Vec3> noLandings = new java.util.ArrayList<>(java.util.List.of(new Vec3(50, 62, -61)));
		assertTrue(Navigator.forgiveShores(blocked, noShore, noLandings));
		assertEquals(java.util.Set.of(1L), blocked, "the shores go; an ordinary strike stays");
		assertTrue(noShore.isEmpty() && noLandings.isEmpty());
		assertFalse(Navigator.forgiveShores(blocked, noShore, noLandings), "nothing left to forgive");
	}

	@Test
	void aLandLegOverWaterWhereHeStartsIsNoMisfit() {
		// Afloat (or on sea ice, water to the map), the route begins with a land leg of nothing at his own cell
		CoarsePlanner.Cells iceHere = (cx, cz) -> cx <= 0 ? new CoarsePlanner.Cell(62, 4, 0, false) : new CoarsePlanner.Cell(64, 0, 0, false);
		CoarsePlanner.Leg start = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(4, 63, 4), new Vec3(4, 63, 4)));
		CoarsePlanner.Leg on = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(12, 64, 4), new Vec3(60, 64, 4)));
		assertNull(Navigator.misfit(List.of(start, on), 0, iceHere, null, Double.MAX_VALUE), "where he is can't be redone");
		CoarsePlanner.Leg across = new CoarsePlanner.Leg(CoarsePlanner.LegKind.LAND, List.of(new Vec3(-20, 64, 4), new Vec3(60, 64, 4)));
		assertNotNull(Navigator.misfit(List.of(across), 0, iceHere, null, Double.MAX_VALUE), "but a land leg over water further on still is");
	}

	@Test
	void anIceFieldStandingAboveTheBargeIsShoreToItAndStruckOffLikeABank() {
		CoarsePlanner.Cell seaIce = new CoarsePlanner.Cell(63, 0, 0, false, 4), iceStep = new CoarsePlanner.Cell(66, 0, 0, false, 4);
		assertFalse(Navigator.isShoreTo(new CoarsePlanner.Cell(64, 0, 0, false, 4), 62.9), "a block of ice on the sheet: climbed, no shore");
		CoarsePlanner.Cell water = new CoarsePlanner.Cell(62, 4, 0, false), land = new CoarsePlanner.Cell(64, 0, 0, false);
		double barge = 62.9;
		assertFalse(Navigator.isShoreTo(seaIce, barge), "sea ice at its level: slid over");
		assertTrue(Navigator.isShoreTo(iceStep, barge), "ice three up: a wall to the barge");
		assertFalse(Navigator.isShoreTo(water, barge));
		assertTrue(Navigator.isShoreTo(land, barge));
		List<Long> struck = Navigator.shoreAround(new Vec3(4, barge, 4), (cx, cz) -> cx > 0 ? iceStep : seaIce);
		assertEquals(3, struck.size(), "the three cells of the ice step beside him, none of the sea ice");
	}

	@Test
	void inTightWaterItSteersForAPointCloserAlongTheWay() {
		List<Vec3> way = List.of(new Vec3(0.5, 62, 0.5), new Vec3(20.5, 62, 0.5));
		Vec3 goal = new Vec3(20.5, 62, 0.5), here = new Vec3(0.5, 62, 0.5);
		assertEquals(0.5 + Navigator.WATER_LOOKAHEAD, Navigator.steerFor(way, false, goal, here, false).x, 1e-6, "open water: well along");
		assertEquals(0.5 + Navigator.TIGHT_LOOKAHEAD, Navigator.steerFor(way, false, goal, here, true).x, 1e-6, "a lead between bergs: close along, so the hull follows the bends");
		assertTrue(Navigator.TIGHT_LOOKAHEAD < Navigator.WATER_LOOKAHEAD);
	}
}
