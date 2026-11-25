package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class WaterPlannerTest {
	/** A map of columns, one character each (row = z, column = x): ~ open water, ! dangerous water, anything else land. */
	private record Lake(String... rows) implements WaterPlanner.Water, WaterPlanner.Danger {
		char at(int x, int z) {
			return z < 0 || z >= rows.length || x < 0 || x >= rows[z].length() ? '#' : rows[z].charAt(x);
		}

		@Override
		public boolean open(int x, int z) {
			return at(x, z) == '~' || at(x, z) == '!';
		}

		@Override
		public double extra(int x, int z) {
			return at(x, z) == '!' ? 20 : 0;
		}
	}

	// A river five blocks wide that comes down and then turns east: the mouth is out of sight round the bend
	private static final Lake BEND = new Lake(
		"#~~~~~##################",
		"#~~~~~##################",
		"#~~~~~##################",
		"#~~~~~##################",
		"#~~~~~##################",
		"#~~~~~##################",
		"#~~~~~~~~~~~~~~~~~~~~~~#",
		"#~~~~~~~~~~~~~~~~~~~~~~#",
		"#~~~~~~~~~~~~~~~~~~~~~~#",
		"#~~~~~~~~~~~~~~~~~~~~~~#",
		"#~~~~~~~~~~~~~~~~~~~~~~#",
		"########################");

	private static void allAfloat(Lake lake, List<Vec3> path, double goalX, double goalZ, double radius) {
		for (Vec3 p : path) {
			int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
			assertTrue(lake.open(x, z), "on land at " + x + "," + z);
			if (Math.hypot(p.x - goalX, p.z - goalZ) > radius + 2)
				assertTrue(WaterPlanner.floats(lake, x, z, goalX, goalZ, radius), "scraping the bank at " + x + "," + z);
		}
	}

	@Test
	void heRowsRoundTheBendRatherThanStraightAtTheBank() {
		List<Vec3> path = WaterPlanner.plan(BEND, BEND, 3, 1, 21.5, 8.5, 2);
		assertFalse(path.isEmpty());
		Vec3 end = path.get(path.size() - 1);
		assertTrue(Math.hypot(end.x - 21.5, end.z - 8.5) <= 2, "gets round to the mouth");
		allAfloat(BEND, path, 21.5, 8.5, 2);
	}

	@Test
	void heComesRightInToTheShoreWhereHeLands() {
		// Landing on the bank at the far end: the last few blocks can run in close to it
		List<Vec3> path = WaterPlanner.plan(BEND, BEND, 3, 1, 23.5, 8.5, 2);
		assertFalse(path.isEmpty());
		Vec3 end = path.get(path.size() - 1);
		assertTrue(Math.hypot(end.x - 23.5, end.z - 8.5) <= 2);
	}

	@Test
	void heRowsTheLongWayRoundToKeepClearOfDanger() {
		// An island in the middle: drowned in the northern channel, so he takes the southern one
		Lake lake = new Lake(
			"###############",
			"#~~~~~~~~~~~~~#",
			"#~~~~!!!!!~~~~#",
			"#~~~~!!!!!~~~~#",
			"#~~~~!!!!!~~~~#",
			"#~~~~#####~~~~#",
			"#~~~~#####~~~~#",
			"#~~~~~~~~~~~~~#",
			"#~~~~~~~~~~~~~#",
			"#~~~~~~~~~~~~~#",
			"#~~~~~~~~~~~~~#",
			"###############");
		List<Vec3> path = WaterPlanner.plan(lake, lake, 2, 4, 12.5, 4.5, 1);
		assertFalse(path.isEmpty());
		for (Vec3 p : path)
			assertTrue(lake.at((int) Math.floor(p.x), (int) Math.floor(p.z)) != '!', "rowed past the drowned at " + p);
	}

	@Test
	void walledInHeHasNoWayAcross() {
		Lake pond = new Lake(
			"#####",
			"#~~~#",
			"#~~~#",
			"#~~~#",
			"#####");
		assertTrue(WaterPlanner.plan(pond, pond, 2, 2, 40, 2, 2).isEmpty(), "nowhere nearer to go");
	}

	@Test
	void theWayKeepsToRoomyWaterWhereItCan() {
		// A channel nine wide with a post standing in it two blocks off the wall: the way goes round the roomy side
		// of the post rather than squeezing between it and the wall
		WaterPlanner.Water water = (x, z) -> x >= 0 && x <= 8 && z >= 0 && z <= 20 && !(x == 2 && z == 10);
		List<Vec3> way = WaterPlanner.plan(water, (x, z) -> 0, 4, 0, 4.5, 20.5, 1);
		assertFalse(way.isEmpty());
		assertTrue(WaterPlanner.tight(water, 1, 10, 4.5, 20.5, 1), "between the post and the wall is tight");
		assertFalse(WaterPlanner.tight(water, 5, 10, 4.5, 20.5, 1), "the other side of the post is roomy");
		for (Vec3 point : way)
			assertFalse(point.x < 2 && Math.abs(point.z - 10) <= 1, "squeezed between the post and the wall at " + point);
	}
}
