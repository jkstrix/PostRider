package com.comedymonkey.postrider.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HarmonicaSoundTest {
	@Test
	void theTuneFadesOutOverTwoSecondsRatherThanCuttingOff() {
		float volume = 1.0F;
		int ticks = 0;
		while (volume > 0) {
			float next = HarmonicaSound.faded(volume);
			assertTrue(next < volume, "quieter every tick");
			volume = next;
			ticks++;
		}
		assertTrue(Math.abs(ticks - HarmonicaSound.FADE_TICKS) <= 1, "gone after two seconds (a tick either way, in float steps): " + ticks);
		assertEquals(0, HarmonicaSound.faded(0.001F), 1e-9, "never below nothing");
	}
}
