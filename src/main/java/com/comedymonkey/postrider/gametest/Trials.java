package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;

/**
 * A whole delivery through a testing world, judged the same way in every test: it fails if he wades, is teleported
 * free, goes round in circles, stands about, or never gets there, and what it took him is noted in the log so the
 * route finding's performance can be compared from one change to the next. Test-only.
 */
final class Trials {
	/** Entering the same few blocks this many times is going round in circles. */
	static final int LOOP_VISITS = 5;
	/** Standing still this long on the road (not planning, camping or at a mailbox) is a stall. */
	static final int STALL_TICKS = 300;

	private Trials() {}

	/**
	 * Succeeds once the mail is in the far box and {@code checks} pass, the rider having been watched all the way;
	 * fails at once on any of the usual signs of trouble.
	 */
	static void deliver(String trial, Scene scene, BlockPos to, Watch watch, Runnable checks) {
		scene.helper.onEachTick(() -> {
			if (watch.waded != null)
				throw new GameTestAssertException(trial + ": he waded into water at " + watch.waded);
			if (watch.jumpsFree() > 0)
				throw new GameTestAssertException(trial + ": he had to be teleported free, at " + watch.lastSeen());
			if (watch.mostVisits > LOOP_VISITS)
				throw new GameTestAssertException(trial + ": he went round in circles, through " + watch.busiest + " " + watch.mostVisits + " times");
			if (watch.longestStill > STALL_TICKS)
				throw new GameTestAssertException(trial + ": he stood still for " + watch.longestStill + " ticks at " + watch.lastSeen() + " (" + watch.run + ")");
		});
		scene.helper.succeedWhen(() -> {
			if (!scene.delivered(to) || scene.stillOut(watch.delivery))
				throw new GameTestAssertException(trial + ": not delivered yet, " + watch.describe());
			if (!watch.studiedMap)
				throw new GameTestAssertException(trial + ": he never studied his map");
			checks.run();
			PostRider.LOGGER.info("Trial {}: delivered; {}", trial, watch.describe());
		});
	}
}
