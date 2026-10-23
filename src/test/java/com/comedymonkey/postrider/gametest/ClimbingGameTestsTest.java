package com.comedymonkey.postrider.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClimbingGameTestsTest {
	@Test
	void theHelixGoesRoundABlockAtATimeNeverCornerToCorner() {
		List<int[]> cells = ClimbingGameTests.helix(31, 31, 9, 2 * ClimbingGameTests.SPIRE_HEIGHT + 1);
		assertEquals(2 * ClimbingGameTests.SPIRE_HEIGHT + 1, cells.size());
		assertEquals(40, cells.get(0)[0], "starts at the east");
		assertEquals(31, cells.get(0)[1]);
		for (int i = 1; i < cells.size(); i++) {
			int[] a = cells.get(i - 1), b = cells.get(i);
			assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]), "a block along at cell " + i);
			double distance = Math.hypot(b[0] - 31, b[1] - 31);
			assertTrue(distance > 8 && distance < 10.5, "round the core, never in it, at cell " + i + ": " + distance);
		}
		// Seven times round: the same cell comes up again a storey higher each time
		int[] first = cells.get(0);
		long again = cells.stream().filter(c -> c[0] == first[0] && c[1] == first[1]).count();
		assertTrue(again >= 7, "turns: " + again);
	}
}
