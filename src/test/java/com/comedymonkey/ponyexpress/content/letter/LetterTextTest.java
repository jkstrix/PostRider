package com.comedymonkey.ponyexpress.content.letter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LetterTextTest {
	/** Every character is 6 wide, so a 30-wide line holds 5 characters. */
	private static LetterText page() {
		return new LetterText(s -> s.length() * 6, 30, 3, 100);
	}

	private static List<String> shown(LetterText text) {
		return text.lines().stream().map(text::lineText).toList();
	}

	@Test
	void anEmptyPageHasOneEmptyLine() {
		LetterText text = page();
		assertEquals(List.of(""), shown(text));
		assertEquals(0, text.cursorLine());
		assertEquals(0, text.cursorX());
	}

	@Test
	void wrapsAtTheLastSpaceThatFits() {
		LetterText text = page();
		assertTrue(text.insert("dear sam hi"));
		assertEquals(List.of("dear ", "sam ", "hi"), shown(text));
	}

	@Test
	void breaksAWordTooLongForALine() {
		LetterText text = page();
		assertTrue(text.insert("abcdefgh"));
		assertEquals(List.of("abcde", "fgh"), shown(text));
	}

	@Test
	void enterStartsANewLine() {
		LetterText text = page();
		text.insert("hi");
		text.insert("\n");
		text.insert("yo");
		assertEquals(List.of("hi", "yo"), shown(text));
		assertEquals(1, text.cursorLine());
		assertEquals(12, text.cursorX());
	}

	@Test
	void refusesTextThatWouldOverflowThePage() {
		LetterText text = page();
		assertTrue(text.insert("a\nb\nc"));
		assertFalse(text.insert("\n"));
		assertEquals("a\nb\nc", text.getText());
		assertTrue(text.insert("cccc"));
		assertFalse(text.insert("d"));
	}

	@Test
	void respectsTheCharacterLimit() {
		LetterText text = new LetterText(s -> 0, 1000, 100, 4);
		assertTrue(text.insert("abcd"));
		assertFalse(text.insert("e"));
	}

	@Test
	void dropsFormattingCodesAndControlCharacters() {
		LetterText text = page();
		text.insert("§ca\tb");
		assertEquals("cab", text.getText());
		assertFalse(text.insert("§"));
	}

	@Test
	void backspaceJoinsLinesAndDeleteRemovesAhead() {
		LetterText text = page();
		text.insert("ab\ncd");
		text.home();
		text.backspace();
		assertEquals("abcd", text.getText());
		assertEquals(2, text.getCursor());
		text.delete();
		assertEquals("abd", text.getText());
	}

	@Test
	void cursorAtAWrapBelongsToTheNextLine() {
		LetterText text = page();
		text.insert("dear sam");
		text.home();
		assertEquals(1, text.cursorLine());
		assertEquals(5, text.getCursor());
		assertEquals(0, text.cursorX());
	}

	@Test
	void upAndDownKeepTheColumn() {
		LetterText text = page();
		text.insert("abcd\nxy\nlmnop");
		assertEquals(2, text.cursorLine());
		text.up();
		assertEquals(1, text.cursorLine());
		assertEquals(text.getText().indexOf("xy") + 2, text.getCursor());
		text.up();
		assertEquals(0, text.cursorLine());
		assertEquals(2, text.getCursor());
		text.up();
		assertEquals(0, text.getCursor());
		text.down();
		text.down();
		text.down();
		assertEquals(text.getText().length(), text.getCursor());
	}

	@Test
	void homeAndEndGoToTheEdgesOfTheLine() {
		LetterText text = page();
		text.insert("ab\ncde");
		text.home();
		assertEquals(3, text.getCursor());
		text.up();
		text.end();
		assertEquals(2, text.getCursor());
	}

	@Test
	void setTextCutsOffWhatDoesNotFit() {
		LetterText text = page();
		text.setText("a\nb\nc\nd\ne");
		assertEquals("a\nb\nc", text.getText());
		assertEquals(text.getText().length(), text.getCursor());
	}
}
