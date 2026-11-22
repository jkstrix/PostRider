package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** He steps up stairs and slabs rather than hop them, but still jumps a step too high to step up. */
class RiderMoveControlTest {
	private static final double STEP = 0.6;

	@Test
	void theLowHalfOfAStairIsSteppedUpNotHopped() {
		// Standing on the low half of a stair block at y 10, its top at 11, making for the block beyond at 11
		assertTrue(RiderMoveControl.hopNeedless(10.5, 11, 11, STEP));
	}

	@Test
	void aSlabIsSteppedUpToo() {
		assertTrue(RiderMoveControl.hopNeedless(10, 10.5, 10.5, STEP));
	}

	@Test
	void aFullBlockUpIsStillJumped() {
		assertFalse(RiderMoveControl.hopNeedless(10, 10, 11, STEP));
		assertFalse(RiderMoveControl.hopNeedless(10, 11, 11, STEP));
	}
}
