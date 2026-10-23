package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.CoarsePlanner.Cell;
import com.comedymonkey.postrider.content.postman.route.CoarsePlanner.LegKind;
import com.comedymonkey.postrider.content.postman.route.CoarsePlanner.Plan;
import com.comedymonkey.postrider.content.postman.route.CoarsePlanner.Status;

import net.minecraft.world.phys.Vec3;

class CoarsePlannerTest {
	private static final Cell LAND = new Cell(64, 0, 0, false);
	private static final Cell WATER = new Cell(62, 4, 0, false);
	private static final Cell VOID = new Cell(-64, 0, 0, true);

	/** A map of cells, one character each (row = z, column = x): . land, ~ water, space for the void; void outside it. */
	private record Map(String... rows) implements CoarsePlanner.Cells {
		@Override
		public Cell cell(int cx, int cz) {
			if (cz < 0 || cz >= rows.length || cx < 0 || cx >= rows[cz].length())
				return VOID;
			return switch (rows[cz].charAt(cx)) {
				case '~' -> WATER;
				case ' ' -> VOID;
				default -> LAND;
			};
		}
	}

	private static Plan plan(CoarsePlanner.Cells cells, int sx, int sz, int gx, int gz) {
		return plan(cells, sx, sz, gx, gz, Set.of());
	}

	private static Plan plan(CoarsePlanner.Cells cells, int sx, int sz, int gx, int gz, Set<Long> blocked) {
		CoarsePlanner planner = new CoarsePlanner(cells, sx, sz, gx, gz, blocked);
		assertEquals(Status.DONE, planner.finishNow());
		return planner.plan();
	}

	/** Whether the plan's lines (followed a block at a time) pass through a cell. */
	private static boolean passesThrough(Plan plan, int cx, int cz) {
		for (CoarsePlanner.Leg leg : plan.legs())
			for (int i = 0; i + 1 < leg.points().size(); i++) {
				Vec3 a = leg.points().get(i), b = leg.points().get(i + 1);
				int samples = (int) Math.ceil(a.distanceTo(b)) + 1;
				for (int j = 0; j <= samples; j++) {
					Vec3 p = a.lerp(b, (double) j / samples);
					if (CoarsePlanner.cellOf(p.x) == cx && CoarsePlanner.cellOf(p.z) == cz)
						return true;
				}
			}
		return false;
	}

	// The bay from the bug report: a narrow inlet between him and the mailbox, with its head not far off
	private static final Map BAY = new Map(
		"..........................",
		"..........................",
		"..........................",
		"..........................",
		"~~~~~~~~~~~~~.............",
		"~~~~~~~~~~~~~.............",
		"~~~~~~~~~~~~~.............",
		"..........................",
		"..........................",
		"..........................");

	@Test
	void heRidesRoundTheBayRatherThanRowingAcrossIt() {
		Plan plan = plan(BAY, 6, 2, 6, 8);
		assertFalse(plan.usesBarge(), "round the head of the bay is less than twice as far");
		assertEquals(1, plan.legs().size());
		assertTrue(plan.legs().get(0).points().stream().anyMatch(p -> CoarsePlanner.cellOf(p.x) >= 13), "round the head");
	}

	@Test
	void aBayThatGoesOnForeverIsCrossedByBarge() {
		String land = ".".repeat(120), water = "~".repeat(110) + ".".repeat(10);
		Map longBay = new Map(land, land, land, water, water, water, land, land, land);
		Plan plan = plan(longBay, 6, 1, 6, 7);
		assertTrue(plan.usesBarge());
		assertEquals(List.of(LegKind.LAND, LegKind.WATER, LegKind.LAND), plan.legs().stream().map(CoarsePlanner.Leg::kind).toList());
		for (Vec3 point : plan.legs().get(1).points())
			assertEquals(WATER, longBay.cell(CoarsePlanner.cellOf(point.x), CoarsePlanner.cellOf(point.z)), "the water leg stays on the water");
	}

	@Test
	void afloatHeRowsToTheNearestLandThatTakesHimOn() {
		// A long channel: he's on the water at its west end, and the mailbox is just past its far end, straight down
		// the water from him. Rowing all the way is about as far as riding, but he'd rather ride
		String land = ".".repeat(40), water = "~".repeat(38) + "..";
		Map channel = new Map(land, land, water, water, water, land, land);
		CoarsePlanner planner = new CoarsePlanner(channel, 2, 3, 39, 3, Set.of(), true);
		assertEquals(Status.DONE, planner.finishNow());
		Plan plan = planner.plan();
		assertEquals(LegKind.WATER, plan.legs().get(0).kind(), "he starts on the water");
		Vec3 ashore = plan.legs().get(1).points().get(0);
		assertTrue(CoarsePlanner.cellOf(ashore.x) <= 4, "ashore at the near bank, not rowing to the far end: " + ashore);
	}

	@Test
	void aRiverIsRowedStraightAcrossNotAlong() {
		// A river right across the map, with the mailbox on the far bank well downstream
		String row = ".".repeat(10) + "~~~~" + ".".repeat(10);
		String[] rows = new String[30];
		java.util.Arrays.fill(rows, row);
		Plan plan = plan(new Map(rows), 2, 2, 20, 27);
		assertTrue(plan.usesBarge());
		CoarsePlanner.Leg water = plan.legs().stream().filter(leg -> leg.kind() == LegKind.WATER).findFirst().orElseThrow();
		int upstream = CoarsePlanner.cellOf(water.points().get(0).z), downstream = CoarsePlanner.cellOf(water.last().z);
		assertTrue(Math.abs(downstream - upstream) <= 1, "straight over, from " + upstream + " to " + downstream);
	}

	@Test
	void anIslandCanOnlyBeReachedByBarge() {
		Map island = new Map(
			"..............",
			"..~~~~~~~~~~..",
			"..~~~~~~~~~~..",
			"..~~~~..~~~~..",
			"..~~~~..~~~~..",
			"..~~~~~~~~~~..",
			"..~~~~~~~~~~..",
			"..............");
		Plan plan = plan(island, 0, 0, 6, 3);
		assertTrue(plan.usesBarge());
		assertEquals(LegKind.LAND, plan.legs().get(plan.legs().size() - 1).kind());
	}

	@Test
	void heNeverCrossesTheVoid() {
		Map gap = new Map(
			"....  ....",
			"....  ....",
			"....  ....",
			"..........",
			"....  ....");
		Plan plan = plan(gap, 1, 0, 8, 0);
		assertTrue(passesThrough(plan, 4, 3) || passesThrough(plan, 5, 3), "over the one bridge of land");
		Map cut = new Map("....  ....", "....  ....");
		assertEquals(Status.FAILED, new CoarsePlanner(cut, 1, 0, 8, 0, Set.of()).finishNow());
	}

	@Test
	void steepGroundIsWorthGoingRound() {
		// A ridge 40 blocks high across the way, with a pass through it at z 6
		CoarsePlanner.Cells ridge = (cx, cz) -> {
			if (cx < 0 || cz < 0 || cx > 20 || cz > 10)
				return VOID;
			return cx == 10 && cz != 6 ? new Cell(104, 0, 0, false) : LAND;
		};
		Plan plan = plan(ridge, 2, 4, 18, 4);
		assertTrue(passesThrough(plan, 10, 6), "through the pass");
	}

	@Test
	void hePlansAroundStretchesHeFoundHeCouldntGetThrough() {
		Map open = new Map(".".repeat(30), ".".repeat(30), ".".repeat(30), ".".repeat(30), ".".repeat(30));
		Set<Long> blocked = new HashSet<>();
		for (int z = 0; z < 4; z++)
			blocked.add(CoarsePlanner.key(15, z));
		Plan plan = plan(open, 2, 1, 28, 1, blocked);
		assertTrue(passesThrough(plan, 15, 4), "round the end of what's blocked");
	}

	@Test
	void theMailboxsOwnCellCanNeverBeMarkedImpassable() {
		// A shore he had to put out from again can be marked, cells round it and all; the mailbox's cell among them
		Map open = new Map(".".repeat(30), ".".repeat(30), ".".repeat(30));
		Set<Long> blocked = new HashSet<>(Set.of(CoarsePlanner.key(28, 1), CoarsePlanner.key(27, 1), CoarsePlanner.key(28, 0)));
		Plan plan = plan(open, 2, 1, 28, 1, blocked);
		assertNotNull(plan, "a way to the mailbox is always found");
		assertEquals(CoarsePlanner.cellOf(plan.legs().get(0).last().x), 28);
	}

	@Test
	void heWaitsForTerrainStillBeingSurveyedAndCarriesOn() {
		Set<Long> asked = new HashSet<>();
		boolean[] arrived = {false};
		CoarsePlanner.Cells slow = (cx, cz) -> {
			if (cx > 10 && !arrived[0]) {
				asked.add(CoarsePlanner.key(cx, cz));
				return null;
			}
			return BAY.cell(cx, cz);
		};
		CoarsePlanner planner = new CoarsePlanner(slow, 6, 2, 6, 8, Set.of());
		Status status = Status.RUNNING;
		for (int i = 0; i < 1000 && status != Status.WAITING; i++)
			status = planner.step(10);
		assertEquals(Status.WAITING, status);
		assertFalse(asked.isEmpty());
		assertEquals(Status.WAITING, planner.step(1000), "still waiting");
		arrived[0] = true;
		assertEquals(Status.DONE, planner.finishNow());
		assertEquals(plan(BAY, 6, 2, 6, 8), planner.plan());
	}

	@Test
	void beforeItsFinishedTheBestSoFarHeadsTheRightWay() {
		Map open = new Map(".".repeat(200), ".".repeat(200), ".".repeat(200));
		CoarsePlanner planner = new CoarsePlanner(open, 2, 1, 190, 1, Set.of());
		planner.step(50);
		Plan partial = planner.bestSoFar();
		assertNotNull(partial);
		Vec3 end = partial.legs().get(partial.legs().size() - 1).last();
		assertTrue(end.x > 2 * CoarsePlanner.CELL + 40, "on towards the goal");
	}

	@Test
	void cellsAreSampledFromFourColumns() {
		int[] floors = {64, 66, 63, 64}, dry = floors.clone();
		Cell land = Cell.of(floors, dry, -64);
		assertFalse(land.water());
		assertEquals(64, land.height());
		assertEquals(3, land.rough());
		Cell lake = Cell.of(new int[] {50, 52, 51, 63}, new int[] {63, 63, 63, 63}, -64);
		assertTrue(lake.water());
		assertEquals(63, lake.height(), "the water's surface");
		assertTrue(Cell.of(new int[] {60, 60, 64, 64}, new int[] {63, 63, 64, 64}, -64).shore());
		assertTrue(Cell.of(new int[] {-64, -64, -64, 70}, new int[] {-64, -64, -64, 70}, -64).isVoid());
	}

	@Test
	void aCliffIsNoSlopeAndIsntCrossedAtAll() {
		// A wall of cells thirteen blocks higher than the ground right across the way (no pass): no way through, and a
		// chasm thirteen deep likewise; a rise of nine across a cell is just steep (a bridge over a chasm, say, that the
		// cells can't see, is for the local plan to find)
		CoarsePlanner.Cells cliff = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 10 ? new Cell(77, 0, 0, false) : LAND;
		assertEquals(Status.FAILED, new CoarsePlanner(cliff, 2, 2, 18, 2, Set.of()).finishNow(), "a cliff of thirteen");
		CoarsePlanner.Cells chasm = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 10 ? new Cell(51, 0, 0, false) : LAND;
		assertEquals(Status.FAILED, new CoarsePlanner(chasm, 2, 2, 18, 2, Set.of()).finishNow(), "a chasm thirteen deep");
		CoarsePlanner.Cells steep = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 10 ? new Cell(73, 0, 0, false) : LAND;
		assertEquals(Status.DONE, new CoarsePlanner(steep, 2, 2, 18, 2, Set.of()).finishNow(), "nine up across a cell is a steep slope, taken");
	}

	@Test
	void aCliffFaceInsideACellIsntCrossedEither() {
		// A wall of cells with nine blocks of height inside each (a cliff face), level with the ground either side
		CoarsePlanner.Cells face = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 10 ? new Cell(64, 0, 9, false) : LAND;
		assertEquals(Status.FAILED, new CoarsePlanner(face, 2, 2, 18, 2, Set.of()).finishNow(), "a cliff face of nine inside the cells");
		CoarsePlanner.Cells rugged = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 10 ? new Cell(64, 0, 8, false) : LAND;
		assertEquals(Status.DONE, new CoarsePlanner(rugged, 2, 2, 18, 2, Set.of()).finishNow(), "eight inside a cell is rugged, ridden");
		CoarsePlanner.Cells boxOnACliff = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : cx == 18 ? new Cell(64, 0, 12, false) : LAND;
		assertEquals(Status.DONE, new CoarsePlanner(boxOnACliff, 2, 2, 18, 2, Set.of()).finishNow(), "the mailbox's own cell may be a cliff");
	}

	@Test
	void theBargeIsntLandedUnderACliff() {
		// A strait between two shores: a cliff coast (a cliff face inside the cells) straight across, a beach further
		// along it; he lands on the beach, not under the cliff
		Cell cliffCoast = new Cell(64, 0, 12, false);
		CoarsePlanner.Cells coast = (cx, cz) -> cx < 0 || cz < 0 || cx > 12 || cz > 8 ? VOID
			: cx >= 4 && cx <= 6 ? WATER
			: cx == 7 && cz <= 6 ? cliffCoast : LAND;
		CoarsePlanner planner = new CoarsePlanner(coast, 2, 2, 10, 2, Set.of());
		assertEquals(Status.DONE, planner.finishNow());
		Plan plan = planner.plan();
		assertNotNull(plan);
		Vec3 landing = plan.legs().get(1).points().get(plan.legs().get(1).points().size() - 1);
		assertEquals(LegKind.WATER, plan.legs().get(1).kind());
		assertTrue(landing.z >= 7 * CoarsePlanner.CELL, "landed on the beach at the end of the cliff coast, not under the cliff: " + landing);
		CoarsePlanner.Cells cliffAll = (cx, cz) -> cx < 0 || cz < 0 || cx > 12 || cz > 8 ? VOID
			: cx >= 4 && cx <= 6 ? WATER : cx == 7 ? cliffCoast : LAND;
		assertEquals(Status.FAILED, new CoarsePlanner(cliffAll, 2, 2, 10, 2, Set.of()).finishNow(), "a cliff coast the whole way: nowhere to land");
	}

	@Test
	void theCellsNextToTheStartAreNeverBlocked() {
		// Everything round the start struck off (strikes from every side of a spot he's stuck at): the plan still leaves it
		Set<Long> ring = new HashSet<>();
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				if (dx != 0 || dz != 0)
					ring.add(CoarsePlanner.key(2 + dx, 2 + dz));
		CoarsePlanner.Cells plain = (cx, cz) -> cx < 0 || cz < 0 || cx > 20 || cz > 4 ? VOID : LAND;
		assertEquals(Status.DONE, new CoarsePlanner(plain, 2, 2, 18, 2, ring).finishNow(), "boxed in by strikes, he still gets out");
		Set<Long> wall = new HashSet<>();
		for (int cz = 0; cz <= 4; cz++)
			wall.add(CoarsePlanner.key(10, cz));
		assertEquals(Status.FAILED, new CoarsePlanner(plain, 2, 2, 18, 2, wall).finishNow(), "a wall of strikes further off still stops him");
	}
}
