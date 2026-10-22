package com.comedymonkey.postrider.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.mailbox.MailboxMenu;

class PostOfficeStyleTest {
	@Test
	void textWrapsAWordAtATime() {
		assertEquals(List.of("Your parcel for", "Alex's Inn is", "waiting."), PostOfficeStyle.wrap("Your parcel for Alex's Inn is waiting.", 15, String::length));
		assertEquals(List.of("short"), PostOfficeStyle.wrap("short", 15, String::length));
	}

	@Test
	void aWordTooLongForALineIsBrokenWhereItHasToBe() {
		assertEquals(List.of("To", "abcdefghij", "klmno"), PostOfficeStyle.wrap("To abcdefghijklmno", 10, String::length));
	}

	@Test
	void aLongMessageStopsAtItsLastLineWithDots() {
		String message = "Posted after the last collection: a rider will collect your parcel for The Long Address Place at 6am.";
		List<String> lines = PostOfficeStyle.clamp(message, 30, 2, String::length);
		assertEquals(2, lines.size(), "it never runs off the parchment");
		assertTrue(lines.get(1).endsWith("..."));
		for (String line : lines)
			assertTrue(line.length() <= 30, line);
		assertEquals(List.of("Sent."), PostOfficeStyle.clamp("Sent.", 30, 2, String::length), "a short one is left alone");
	}

	@Test
	void theMailboxIsWiderThanAChestWithTheInventoryInTheMiddle() {
		assertTrue(MailboxMenu.WIDTH >= 240);
		int left = MailboxMenu.INVENTORY_X - 1, right = MailboxMenu.WIDTH - (left + 9 * 18);
		assertEquals(left, right);
	}
}
