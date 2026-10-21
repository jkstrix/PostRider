package com.comedymonkey.ponyexpress.content.letter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

class LetterDraftTest {
	@Test
	void sanitizeKeepsAtMostFourPages() {
		List<String> pages = LetterDraft.sanitize(List.of("1", "2", "3", "4", "5", "6"));
		assertEquals(List.of("1", "2", "3", "4"), pages);
	}

	@Test
	void sanitizeShortensLongPages() {
		List<String> pages = LetterDraft.sanitize(List.of("x".repeat(2000)));
		assertEquals(LetterDraft.MAX_PAGE_LENGTH, pages.get(0).length());
	}

	@Test
	void sanitizeStripsFormattingButKeepsLineBreaks() {
		assertEquals(List.of("lDear Alex,\nhello"), LetterDraft.sanitize(List.of("§lDear Alex,\n\thello\u0000")));
	}

	@Test
	void sanitizeDropsBlankPagesAtTheEnd() {
		assertEquals(List.of("hi", "", "there"), LetterDraft.sanitize(List.of("hi", "", "there", " ", "")));
		assertTrue(LetterDraft.sanitize(List.of("", "  ")).isEmpty());
	}

	@Test
	void blankMeansNothingButWhitespace() {
		assertTrue(new LetterDraft(List.of(" ", "\n")).isBlank());
		assertFalse(new LetterDraft(List.of(" ", "x")).isBlank());
	}

	@Test
	void draftsAndLettersSurviveSaving() {
		LetterDraft draft = new LetterDraft(List.of("one", "two"));
		Tag draftTag = LetterDraft.CODEC.encodeStart(NbtOps.INSTANCE, draft).getOrThrow();
		assertEquals(draft, LetterDraft.CODEC.parse(NbtOps.INSTANCE, draftTag).getOrThrow());

		LetterContent letter = new LetterContent(List.of("Howdy"), "Steve", Optional.of(new LetterContent.Postmark("Ranch", 12)));
		Tag letterTag = LetterContent.CODEC.encodeStart(NbtOps.INSTANCE, letter).getOrThrow();
		assertEquals(letter, LetterContent.CODEC.parse(NbtOps.INSTANCE, letterTag).getOrThrow());

		LetterContent unposted = new LetterContent(List.of("Howdy"), "Steve", Optional.empty());
		Tag unpostedTag = LetterContent.CODEC.encodeStart(NbtOps.INSTANCE, unposted).getOrThrow();
		assertEquals(unposted, LetterContent.CODEC.parse(NbtOps.INSTANCE, unpostedTag).getOrThrow());
	}

	@Test
	void postmarkingKeepsTheWordsAndAuthor() {
		LetterContent letter = new LetterContent(List.of("Howdy"), "Steve", Optional.empty());
		LetterContent marked = letter.postmarked(new LetterContent.Postmark("Ranch", 3));
		assertEquals(letter.pages(), marked.pages());
		assertEquals("Steve", marked.author());
		assertEquals("Ranch", marked.postmark().orElseThrow().from());
	}
}
