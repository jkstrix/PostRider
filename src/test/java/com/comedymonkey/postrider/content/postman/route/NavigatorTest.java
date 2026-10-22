package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
		assertEquals(0, Navigator.firstToHeadFor(line, 0, 3, 0.2, 0.5));
		assertEquals(2, Navigator.firstToHeadFor(line, 0, 3, 1.9, 0.5), "between the second and third: on to the third");
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
		assertEquals(Navigator.Failed.BARGE, Navigator.whenStretchFails(false, true, true, 0));
		assertEquals(Navigator.Failed.BARGE, Navigator.whenStretchFails(true, true, true, 0), "even with the mailbox over the water");
		// No water: the plan is redone round whatever's in the way
		assertEquals(Navigator.Failed.REPLAN, Navigator.whenStretchFails(false, false, true, 0));
		assertEquals(Navigator.Failed.DROP_PLAN, Navigator.whenStretchFails(false, false, true, Navigator.MAX_BLOCKED));
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(true, false, true, 0), "can't reach the mailbox itself: getting unstuck sorts it");
		assertEquals(Navigator.Failed.CARRY_ON, Navigator.whenStretchFails(false, false, false, 0));
	}
}
