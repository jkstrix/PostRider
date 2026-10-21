package com.comedymonkey.ponyexpress.content.letter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One page of a letter being written: the text, a cursor, and how the text wraps onto lines of a given width.
 * Knows nothing about rendering, so the width of text is passed in.
 */
public class LetterText {
	/** A wrapped line: characters [start, end) of the page; a trailing space it broke at belongs to it. */
	public record Line(int start, int end) {}

	private final ToIntFunction<String> width;
	private final int maxWidth;
	private final int maxLines;
	private final int maxLength;
	private String text = "";
	private int cursor;

	public LetterText(ToIntFunction<String> width, int maxWidth, int maxLines, int maxLength) {
		this.width = width;
		this.maxWidth = maxWidth;
		this.maxLines = maxLines;
		this.maxLength = maxLength;
	}

	public String getText() {
		return text;
	}

	public int getCursor() {
		return cursor;
	}

	/** Replaces the page, putting the cursor at the end; text that doesn't fit is cut short. */
	public void setText(String newText) {
		text = "";
		cursor = 0;
		for (int i = 0; i < newText.length(); i++)
			if (!insert(String.valueOf(newText.charAt(i))))
				break;
	}

	public List<Line> lines() {
		return layout(text);
	}

	/** Types text at the cursor. False, and nothing changes, if it wouldn't fit on the page. */
	public boolean insert(String typed) {
		StringBuilder clean = new StringBuilder();
		for (char c : typed.toCharArray())
			if (c == '\n' || (c >= ' ' && c != '§' && c != '\u007f'))
				clean.append(c == '\r' ? '\n' : c);
		if (clean.isEmpty())
			return false;
		String candidate = text.substring(0, cursor) + clean + text.substring(cursor);
		if (candidate.length() > maxLength || layout(candidate).size() > maxLines)
			return false;
		text = candidate;
		cursor += clean.length();
		return true;
	}

	public void backspace() {
		if (cursor > 0) {
			text = text.substring(0, cursor - 1) + text.substring(cursor);
			cursor--;
		}
	}

	public void delete() {
		if (cursor < text.length())
			text = text.substring(0, cursor) + text.substring(cursor + 1);
	}

	public void left() {
		cursor = Math.max(0, cursor - 1);
	}

	public void right() {
		cursor = Math.min(text.length(), cursor + 1);
	}

	public void home() {
		cursor = lines().get(cursorLine()).start();
	}

	public void end() {
		Line line = lines().get(cursorLine());
		cursor = visibleEnd(line);
	}

	public void up() {
		moveVertically(-1);
	}

	public void down() {
		moveVertically(1);
	}

	/** Which wrapped line the cursor is on. */
	public int cursorLine() {
		List<Line> lines = lines();
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			boolean last = i == lines.size() - 1;
			if (cursor >= line.start() && (cursor < line.end() || (cursor == line.end() && (last || endsWithBreak(line)))))
				return i;
		}
		return lines.size() - 1;
	}

	/** How far across its line the cursor is, in the same units as the width function. */
	public int cursorX() {
		Line line = lines().get(cursorLine());
		return width.applyAsInt(text.substring(line.start(), cursor));
	}

	/** The text of a line as it should be drawn (without its line break). */
	public String lineText(Line line) {
		return text.substring(line.start(), visibleEnd(line));
	}

	private boolean endsWithBreak(Line line) {
		return line.end() < text.length() && text.charAt(line.end()) == '\n';
	}

	private int visibleEnd(Line line) {
		return line.end();
	}

	private void moveVertically(int direction) {
		List<Line> lines = lines();
		int target = cursorLine() + direction;
		if (target < 0) {
			cursor = 0;
			return;
		}
		if (target >= lines.size()) {
			cursor = text.length();
			return;
		}
		int x = cursorX();
		Line line = lines.get(target);
		int best = line.start();
		for (int i = line.start(); i <= visibleEnd(line); i++) {
			if (width.applyAsInt(text.substring(line.start(), i)) > x)
				break;
			best = i;
		}
		cursor = best;
	}

	/** Wraps text word by word to the width; explicit line breaks always start a new line. */
	List<Line> layout(String page) {
		List<Line> lines = new ArrayList<>();
		int paragraphStart = 0;
		while (true) {
			int paragraphEnd = page.indexOf('\n', paragraphStart);
			if (paragraphEnd < 0)
				paragraphEnd = page.length();
			wrapParagraph(page, paragraphStart, paragraphEnd, lines);
			if (paragraphEnd >= page.length())
				break;
			paragraphStart = paragraphEnd + 1;
		}
		return lines;
	}

	private void wrapParagraph(String page, int start, int end, List<Line> lines) {
		if (start == end) {
			lines.add(new Line(start, end));
			return;
		}
		int lineStart = start;
		while (lineStart < end) {
			int fit = lineStart;
			while (fit < end && width.applyAsInt(page.substring(lineStart, fit + 1)) <= maxWidth)
				fit++;
			if (fit >= end) {
				lines.add(new Line(lineStart, end));
				return;
			}
			int space = page.lastIndexOf(' ', fit);
			int breakAt = space > lineStart ? space + 1 : Math.max(fit, lineStart + 1);
			lines.add(new Line(lineStart, breakAt));
			lineStart = breakAt;
		}
	}
}
