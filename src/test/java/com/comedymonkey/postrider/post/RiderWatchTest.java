package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiderWatchTest {
	@Test
	void aRiderMissingForAMinuteIsBroughtBack() {
		assertFalse(RiderWatch.longGone(1000, 1000));
		assertFalse(RiderWatch.longGone(1000, 1000 + 59 * 20), "maybe still loading after a restart");
		assertTrue(RiderWatch.longGone(1000, 1000 + 60 * 20));
	}

	@Test
	void theWatchRunsWithTheDispatcher() {
		// Checked every time the post office looks for mail to send, so well within the minute's grace
		assertTrue(PostDispatcher.INTERVAL < RiderWatch.GRACE_TICKS / 10);
	}
}
