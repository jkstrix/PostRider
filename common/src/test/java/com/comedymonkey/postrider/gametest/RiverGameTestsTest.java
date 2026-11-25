package com.comedymonkey.postrider.gametest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sums the river trials judge a ride by. */
class RiverGameTestsTest {
	@Test
	void crossingTheMiddleRowCountsEitherWay() {
		assertTrue(RiverGameTests.crossed(30, 33, 32));
		assertTrue(RiverGameTests.crossed(33, 30, 32));
		assertTrue(RiverGameTests.crossed(31.9, 32, 32), "landing exactly on the line from the south counts");
	}

	@Test
	void stayingOnOneSideDoesnt() {
		assertFalse(RiverGameTests.crossed(30, 31.9, 32));
		assertFalse(RiverGameTests.crossed(33, 32, 32), "coming down to the line from the north is still north of it");
		assertFalse(RiverGameTests.crossed(32, 32, 32));
	}

	@Test
	void aPatchCoversItsBlocksToTheirFarEdges() {
		assertTrue(RiverGameTests.within(66, 20, 66, 20, 70, 24));
		assertTrue(RiverGameTests.within(70.9, 24.9, 66, 20, 70, 24));
		assertFalse(RiverGameTests.within(71, 22, 66, 20, 70, 24));
		assertFalse(RiverGameTests.within(68, 19.9, 66, 20, 70, 24));
	}
}
