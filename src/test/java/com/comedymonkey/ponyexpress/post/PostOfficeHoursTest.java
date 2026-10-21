package com.comedymonkey.ponyexpress.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.testutil.ModResourceFiles;
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
	void sendersAreToldWhenTheirMailWaitsForMorning() {
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "ponyexpress", "lang", "en_us.json")).getAsJsonObject();
		assertTrue(lang.get("message.ponyexpress.mailbox.queued_night").getAsString().contains("6am"));
		assertTrue(lang.get("message.ponyexpress.mailbox.queued_letter_night").getAsString().contains("6am"));
	}
}
