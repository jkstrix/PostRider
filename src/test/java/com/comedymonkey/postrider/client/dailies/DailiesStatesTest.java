package com.comedymonkey.postrider.client.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;

import net.minecraft.world.InteractionHand;

/** What Post Rider's screens are recorded as for Dailies reads back as it was written. */
class DailiesStatesTest {
	private static final LetterContent LETTER = new LetterContent(List.of("Dear Alex,\nthe barge is late.", "Yours"), "Sam",
		Optional.of(new LetterContent.Postmark("Brasswick", 12, 2400)));

	@Test
	void theMailboxReadsBackAsWritten() {
		DailiesStates.Mailbox sending = new DailiesStates.Mailbox(MailboxMenu.TAB_SEND, "Brasswick", 3, "");
		assertEquals(sending, DailiesStates.readMailbox(DailiesStates.mailbox(sending)));
		DailiesStates.Mailbox naming = new DailiesStates.Mailbox(MailboxMenu.TAB_SETTINGS, null, 0, "Alex's In");
		assertEquals(naming, DailiesStates.readMailbox(DailiesStates.mailbox(naming)));
	}

	@Test
	void aLetterInHandIsKeptAsTheHandAndACheck() {
		DailiesStates.Reading held = new DailiesStates.Reading(InteractionHand.OFF_HAND, DailiesStates.check(LETTER), null, 1);
		DailiesStates.Reading read = DailiesStates.readReading(DailiesStates.reading(held));
		assertEquals(held, read);
		assertNull(read.copy());
		// The check tells another letter apart, and is the same for the same letter
		assertEquals(DailiesStates.check(LETTER), DailiesStates.check(new LetterContent(LETTER.pages(), LETTER.author(), LETTER.postmark())));
		assertNotEquals(DailiesStates.check(LETTER), DailiesStates.check(new LetterContent(LETTER.pages(), "Someone else", LETTER.postmark())));
	}

	@Test
	void aLetterNotInHandIsCopied() {
		DailiesStates.Reading copied = new DailiesStates.Reading(null, 0, LETTER, 0);
		DailiesStates.Reading read = DailiesStates.readReading(DailiesStates.reading(copied));
		assertEquals(LETTER, read.copy());
		assertNull(read.hand());
		assertEquals(0, read.page());
	}

	@Test
	void aLetterBeingWrittenReadsBackAsWritten() {
		DailiesStates.Writing writing = new DailiesStates.Writing(List.of("Dear Alex,\nI hope", ""), 1, 0, false);
		assertEquals(writing, DailiesStates.readWriting(DailiesStates.writing(writing)));
		DailiesStates.Writing sealing = new DailiesStates.Writing(List.of("Done."), 0, 5, true);
		assertEquals(sealing, DailiesStates.readWriting(DailiesStates.writing(sealing)));
	}
}
