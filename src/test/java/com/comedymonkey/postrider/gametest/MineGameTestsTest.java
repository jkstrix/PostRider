package com.comedymonkey.postrider.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MineGameTestsTest {
	@Test
	void theStairGoesDownABlockAtATimeTurningBackAtEachLanding() {
		List<int[]> cells = MineGameTests.stair();
		int[] first = cells.get(0), last = cells.get(cells.size() - 1);
		assertEquals(MineGameTests.STAIR_X, first[0]);
		assertEquals(MineGameTests.PLATEAU, first[2], "in at the top, a block below the plateau's surface");
		assertEquals(MineGameTests.BEDROCK_FLOOR, last[2], "down to the bedrock");
		int landings = 0;
		for (int i = 1; i < cells.size(); i++) {
			int[] a = cells.get(i - 1), b = cells.get(i);
			assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]), "a block along at cell " + i);
			assertTrue(a[2] - b[2] == 1 || a[2] == b[2], "a block down or flat at cell " + i);
			assertTrue(b[0] >= MineGameTests.WEST_LANDING && b[0] <= MineGameTests.EAST_LANDING, "between the landings at cell " + i);
			assertTrue(b[1] >= MineGameTests.STAIR_Z && b[1] <= MineGameTests.BACK_Z, "on one of the two rows or crossing between them at cell " + i);
			if (a[1] != b[1])
				landings++;
		}
		assertTrue(landings >= 3 * 3, "three turns at least, each three cells across: " + landings);
	}
}
