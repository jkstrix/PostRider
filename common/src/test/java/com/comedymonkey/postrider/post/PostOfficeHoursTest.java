package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.google.gson.JsonObject;

class PostOfficeHoursTest {
	@Test
	void openFromSixInTheMorningUntilDusk() {
		assertTrue(PostOfficeHours.isOpen(0));
		assertTrue(PostOfficeHours.isOpen(6000));
		assertTrue(PostOfficeHours.isOpen(11999));
		assertFalse(PostOfficeHours.isOpen(12000));
		assertFalse(PostOfficeHours.isOpen(18000));
		assertFalse(PostOfficeHours.isOpen(23999));
	}

	@Test
	void worksOnAnyDayNotJustTheFirst() {
		assertTrue(PostOfficeHours.isOpen(24000 * 5 + 100));
		assertFalse(PostOfficeHours.isOpen(24000 * 5 + 13000));
		// Day time can be negative after /time commands; 5am the day before is still night
		assertFalse(PostOfficeHours.isOpen(-1000));
		assertTrue(PostOfficeHours.isOpen(-24000 + 100));
	}

	@Test
	void mailPostedAtNightWaitsUntilSixAm() {
		assertEquals(0, PostOfficeHours.ticksUntilOpen(500));
		assertEquals(12000, PostOfficeHours.ticksUntilOpen(12000));
		assertEquals(1, PostOfficeHours.ticksUntilOpen(23999));
		assertEquals(6000, PostOfficeHours.ticksUntilOpen(24000 * 3 + 18000));
	}

	@Test
	void sendersAreToldWhenTheirMailWillBeCollected() {
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "lang", "en_us.json")).getAsJsonObject();
		for (String key : new String[] {"message.postrider.mailbox.queued_later", "message.postrider.mailbox.queued_letter_later"})
			assertEquals(2, lang.get(key).getAsString().split("%s", -1).length - 1, key + " names the address and the time");
	}

	@Test
	void byDefaultRidersSetOutFromSixUntilDusk() {
		assertTrue(PostOfficeHours.isCollecting(0, 6, 18), "6am");
		assertTrue(PostOfficeHours.isCollecting(11999, 6, 18));
		assertFalse(PostOfficeHours.isCollecting(12000, 6, 18), "dusk");
		assertFalse(PostOfficeHours.isCollecting(23999, 6, 18), "5:59am");
		for (long time = 0; time < 24000; time += 250)
			assertEquals(PostOfficeHours.isOpen(time), PostOfficeHours.isCollecting(time, 6, 18), "the same as the old opening hours at " + time);
	}

	@Test
	void collectionHoursCanBeMovedOrRunPastMidnight() {
		assertFalse(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(8) - 1, 9, 17), "7:59am");
		assertTrue(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(9), 9, 17));
		assertFalse(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(17), 9, 17));
		assertTrue(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(23), 20, 4), "a night service");
		assertTrue(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(2), 20, 4));
		assertFalse(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(12), 20, 4));
		assertTrue(PostOfficeHours.isCollecting(PostOfficeHours.dayTimeOf(3), 6, 6), "round the clock");
	}

	@Test
	void collectionTimesAreWrittenLikeAClock() {
		assertEquals("6am", PostOfficeHours.clock(6));
		assertEquals("noon", PostOfficeHours.clock(12));
		assertEquals("6pm", PostOfficeHours.clock(18));
		assertEquals("midnight", PostOfficeHours.clock(0));
		assertEquals("midnight", PostOfficeHours.clock(24));
		assertEquals(0, PostOfficeHours.dayTimeOf(6));
		assertEquals(18000, PostOfficeHours.dayTimeOf(0));
	}
}
