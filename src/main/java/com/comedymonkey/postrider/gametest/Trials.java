package com.comedymonkey.postrider.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.platform.Services;

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
			String trouble = watch.waded != null ? "he waded into water at " + watch.waded
				: watch.jumpsFree() > 0 ? "he had to be teleported free, at " + watch.lastSeen()
				: watch.mostVisits > LOOP_VISITS ? "he went round in circles, through " + watch.busiest + " " + watch.mostVisits + " times"
				: watch.longestStill > STALL_TICKS ? "he stood still for " + watch.longestStill + " ticks at " + watch.lastSeen() + " (" + watch.run + ")"
				: null;
			if (trouble != null) {
				record(trial, watch, false);
				throw new GameTestAssertException(trial + ": " + trouble);
			}
		});
		scene.helper.succeedWhen(() -> {
			if (!scene.delivered(to) || scene.stillOut(watch.delivery))
				throw new GameTestAssertException(trial + ": not delivered yet, " + watch.describe());
			if (!watch.studiedMap)
				throw new GameTestAssertException(trial + ": he never studied his map");
			checks.run();
			record(trial, watch, true);
			PostRider.LOGGER.info("Trial {}: delivered; {}", trial, watch.describe());
		});
	}

	/** The results file: one line of JSON per trial, in the game folder (run-gametest/trials.jsonl on a test run). */
	static final Path RESULTS = Services.PLATFORM.gameDirectory().resolve("trials.jsonl");
	private static boolean cleared;

	/** Notes how a trial went in the results file (started afresh each run), for tools/trials.py to compare. */
	static void record(String trial, Watch watch, boolean passed) {
		try {
			if (!cleared) {
				Files.deleteIfExists(RESULTS);
				cleared = true;
			}
			Files.writeString(RESULTS, watch.json(trial, passed) + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			PostRider.LOGGER.warn("Couldn't note the trial {} in {}", trial, RESULTS, e);
		}
	}
}
