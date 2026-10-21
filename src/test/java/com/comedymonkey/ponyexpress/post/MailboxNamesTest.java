package com.comedymonkey.ponyexpress.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MailboxNamesTest {
	@Test
	void cleanTrimsAndCollapsesSpaces() {
		assertEquals("Steve's Ranch", MailboxNames.clean("  Steve's \t  Ranch  "));
	}

	@Test
	void acceptsOrdinaryAddresses() {
		assertNull(MailboxNames.check("Steve's Ranch"));
		assertNull(MailboxNames.check("Town Hall #2"));
		assertNull(MailboxNames.check("Bob & Sue (North)"));
		assertNull(MailboxNames.check("Café"));
	}

	@Test
	void lengthLimitsAreThreeToTwentyFour() {
		assertEquals(MailboxNames.Problem.TOO_SHORT, MailboxNames.check("ab"));
		assertNull(MailboxNames.check("abc"));
		assertNull(MailboxNames.check("a".repeat(24)));
		assertEquals(MailboxNames.Problem.TOO_LONG, MailboxNames.check("a".repeat(25)));
	}

	@Test
	void rejectsSymbolsAndFormattingCodes() {
		assertEquals(MailboxNames.Problem.BAD_CHARACTER, MailboxNames.check("§cRed Box"));
		assertEquals(MailboxNames.Problem.BAD_CHARACTER, MailboxNames.check("a/b/c"));
		assertEquals(MailboxNames.Problem.BAD_CHARACTER, MailboxNames.check("Tab\there"));
	}

	@Test
	void keyIgnoresCaseAndSpacing() {
		assertEquals(MailboxNames.key("Steve's Ranch"), MailboxNames.key("  steve's   RANCH "));
	}
}
