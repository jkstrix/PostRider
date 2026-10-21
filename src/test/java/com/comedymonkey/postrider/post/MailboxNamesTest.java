package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

	@Test
	void anAddressAnotherMailboxHasIsTakenWhateverTheCase() {
		List<String> others = List.of("Steve's Ranch", "Beach House");
		assertTrue(MailboxNames.isTaken("beach house", others));
		assertTrue(MailboxNames.isTaken("STEVE'S RANCH", others));
		assertFalse(MailboxNames.isTaken("Beach Hut", others));
	}

	@Test
	void everyProblemHasAMessage() {
		for (MailboxNames.Problem problem : MailboxNames.Problem.values())
			assertTrue(MailboxNames.describe(problem).getContents().toString().contains("message.postrider.mailbox.name_"), problem.name());
	}
}
