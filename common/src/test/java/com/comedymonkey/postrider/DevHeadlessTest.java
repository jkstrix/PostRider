package com.comedymonkey.postrider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DevHeadlessTest {
	@Test
	void theBoxesAreNamedWithABarBetween() {
		assertArrayEquals(new String[] {"Testing", "Smelly"}, DevHeadless.parse("Testing|Smelly"));
		assertArrayEquals(new String[] {"Beach House", "My Post"}, DevHeadless.parse(" Beach House | My Post "), "spaces in names, and round the bar");
		assertNull(DevHeadless.parse("Testing"), "one name is no trip");
		assertNull(DevHeadless.parse("Testing|"), "nor a blank one");
		assertNull(DevHeadless.parse(null));
	}

	@Test
	void aSpotAskedForIsTriedFirstThenTheNearestRoundItWithinReach() {
		java.util.List<int[]> spots = DevHeadless.spotsRound(1969, 1794);
		assertArrayEquals(new int[] {1969, 1794}, spots.get(0), "the spot itself first");
		double last = 0;
		for (int[] spot : spots) {
			double off = Math.hypot(spot[0] - 1969, spot[1] - 1794);
			org.junit.jupiter.api.Assertions.assertTrue(off >= last, "nearest first");
			org.junit.jupiter.api.Assertions.assertTrue(Math.max(Math.abs(spot[0] - 1969), Math.abs(spot[1] - 1794)) <= DevHeadless.AT_REACH, "never beyond reach");
			last = off;
		}
		org.junit.jupiter.api.Assertions.assertEquals(16, DevHeadless.AT_REACH);
		org.junit.jupiter.api.Assertions.assertTrue(spots.size() > 200, "a ring of spots every two blocks out to the reach");
	}
}
