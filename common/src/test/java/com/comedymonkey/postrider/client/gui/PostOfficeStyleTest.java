package com.comedymonkey.postrider.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.post.MailboxNames;
import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.google.gson.JsonObject;

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

	/** Roughly how wide Minecraft's font draws text, erring wide: most characters 6 pixels, thin ones less, spaces 4. */
	private static int fontWidth(String text) {
		int width = 0;
		for (char c : text.toCharArray())
			width += c == ' ' ? 4 : "il.,'!:;|".indexOf(c) >= 0 ? 3 : 6;
		return width;
	}

	@Test
	void whatHappenedToYourMailFitsUnderTheLedgerOnTheParchment() {
		assertTrue(MailboxScreen.SEND_NOTICE_Y + MailboxScreen.SEND_NOTICE_LINES * 9 <= MailboxScreen.PARCHMENT_BOTTOM, "both lines on the parchment");
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "lang", "en_us.json")).getAsJsonObject();
		// A long address (the most an address can be) and the latest collection time
		String address = "Steve's Ranch and Stable";
		assertEquals(MailboxNames.MAX_LENGTH, address.length());
		for (String key : new String[] {"queued", "queued_letter", "queued_later", "queued_letter_later"}) {
			String message = lang.get("message.postrider.mailbox." + key).getAsString().replaceFirst("%s", address).replace("%s", "12pm");
			List<String> lines = PostOfficeStyle.wrap(message, MailboxScreen.SEND_NOTICE_WIDTH, PostOfficeStyleTest::fontWidth);
			assertTrue(lines.size() <= MailboxScreen.SEND_NOTICE_LINES, key + " runs to " + lines);
		}
	}

	@Test
	void theSettingsTextFitsAboveTheWood() {
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "lang", "en_us.json")).getAsJsonObject();
		String rules = lang.get("gui.postrider.mailbox.name_rules").getAsString().replaceFirst("%s", "3").replace("%s", "24");
		assertEquals(1, PostOfficeStyle.wrap(rules, MailboxScreen.RULES_WIDTH, PostOfficeStyleTest::fontWidth).size(), "the rules take one line");
		assertTrue(MailboxScreen.RULES_Y + MailboxScreen.STATUS_LINES * 9 <= MailboxScreen.PARCHMENT_BOTTOM, "what's wrong takes their place, on the parchment");
		// Every message about an address fits whole, even with the longest address
		String address = "Steve's Ranch and Stable";
		// On the Settings tab
		for (String key : new String[] {"name_taken", "name_bad_character", "name_too_short", "name_too_long", "named"}) {
			String message = lang.get("message.postrider.mailbox." + key).getAsString().replace("%s", address);
			assertTrue(PostOfficeStyle.wrap(message, MailboxScreen.RULES_WIDTH, PostOfficeStyleTest::fontWidth).size() <= MailboxScreen.STATUS_LINES, key);
		}
		// On the Send tab
		for (String key : new String[] {"other_dimension", "unknown_address", "unnamed", "empty_parcel"}) {
			String message = lang.get("message.postrider.mailbox." + key).getAsString().replace("%s", address);
			assertTrue(PostOfficeStyle.wrap(message, MailboxScreen.SEND_NOTICE_WIDTH, PostOfficeStyleTest::fontWidth).size() <= MailboxScreen.SEND_NOTICE_LINES, key);
		}
	}

	@Test
	void theNamePlaqueStretchesToFitTheName() {
		assertEquals(PostOfficeStyle.PLAQUE_WIDTH, PostOfficeStyle.plaqueWidth(30, MailboxMenu.WIDTH), "a short name: as drawn");
		int longest = fontWidth("WWWWWWWWWWWWWWWWWWWWWWWW");
		assertTrue(PostOfficeStyle.plaqueWidth(fontWidth("Steve's Ranch and Stable"), MailboxMenu.WIDTH) >= fontWidth("Steve's Ranch and Stable") + 12);
		assertTrue(PostOfficeStyle.plaqueWidth(longest, MailboxMenu.WIDTH) <= MailboxMenu.WIDTH - 64, "never over the tabs' corners");
	}
}
