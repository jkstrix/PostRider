package com.comedymonkey.postrider.parity;

import java.io.File;

import javax.xml.parsers.ParserConfigurationException;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;

/**
 * Writes the game test results as a JUnit report, the same way on every mod loader, so the loaders' results can be
 * compared (loader-parity-check). Only when the game is started with -Dparity.gametest.report=&lt;file&gt;. The usual log
 * lines are still written.
 */
public final class ParityGameTests {
	public static final String PROPERTY = "parity.gametest.report";

	private ParityGameTests() {}

	/** When the mod starts: puts the report in place if the game was started for it. */
	public static void installReporterIfRequested() {
		String out = System.getProperty(PROPERTY);
		if (out == null || out.isBlank())
			return;
		File file = new File(out).getAbsoluteFile();
		file.getParentFile().mkdirs();
		try {
			TestReporter junit = new JUnitLikeTestReporter(file);
			TestReporter log = new LogTestReporter();
			GlobalTestReporter.replaceWith(new TestReporter() {
				@Override
				public void onTestFailed(GameTestInfo test) {
					log.onTestFailed(test);
					junit.onTestFailed(test);
				}

				@Override
				public void onTestSuccess(GameTestInfo test) {
					log.onTestSuccess(test);
					junit.onTestSuccess(test);
				}

				@Override
				public void finish() {
					log.finish();
					junit.finish();
					PostRider.LOGGER.info("Post Rider game test report written to {}", file);
				}
			});
		} catch (ParserConfigurationException e) {
			PostRider.LOGGER.error("Post Rider game test report could not be set up", e);
		}
	}
}
