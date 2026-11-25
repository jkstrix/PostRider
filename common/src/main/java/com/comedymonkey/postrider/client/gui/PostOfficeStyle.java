package com.comedymonkey.postrider.client.gui;

import com.comedymonkey.postrider.PostRider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The look shared by every Post Rider screen: walnut panelling, a parchment work surface, brass fittings and
 * postage-stamp tabs, all drawn from one sheet (textures/gui/post_office.png) so the screens stay consistent.
 */
public final class PostOfficeStyle {
	public static final Identifier SHEET = PostRider.asResource("textures/gui/post_office.png");
	public static final int SHEET_SIZE = 256;

	public static final int PANEL_WIDTH = 176;
	public static final int PANEL_HEIGHT = 192;

	/** Ink on parchment, engraving on brass, faded pencil for hints, and cream for text on wood or wax. */
	public static final int INK = 0xFF3B2A1C;
	public static final int ENGRAVED = 0xFF3A2A10;
	public static final int PENCIL = 0xFF6B5A40;
	public static final int CREAM = 0xFFE8D9B5;
	public static final int WAX_TEXT = 0xFFF1E6CC;
	public static final int INK_ERROR = 0xFF9E2A22;
	public static final int LEDGER_SELECTED = 0x593C5A8A;
	public static final int LEDGER_HOVER = 0x2A3C5A8A;

	public enum Slot {
		PIGEONHOLE(176),
		KRAFT(194),
		BRASS(212);

		final int u;

		Slot(int u) {
			this.u = u;
		}
	}

	public enum ButtonStyle {
		BRASS(124, 140, 156, ENGRAVED),
		WAX(172, 188, 156, WAX_TEXT);

		final int v;
		final int hoverV;
		final int disabledV;
		final int textColor;

		ButtonStyle(int v, int hoverV, int disabledV, int textColor) {
			this.v = v;
			this.hoverV = hoverV;
			this.disabledV = disabledV;
			this.textColor = textColor;
		}
	}

	public static final int BUTTON_WIDTH = 56;
	public static final int BUTTON_HEIGHT = 16;
	public static final int TAB_WIDTH = 30;
	public static final int TAB_SPACING = 32;
	public static final int TAB_HEIGHT = 26;
	public static final int TAB_ACTIVE_HEIGHT = 28;
	public static final int TEXT_FIELD_WIDTH = 160;
	public static final int TEXT_FIELD_HEIGHT = 14;
	public static final int LEDGER_WIDTH = 100;
	public static final int LEDGER_HEIGHT = 46;
	public static final int LEDGER_ROW = 10;
	public static final int PLAQUE_WIDTH = 110;
	public static final int PLAQUE_HEIGHT = 13;

	/** Tab icons on the sheet, in tab order: envelope, parcel, quill. */
	public static final int ICON_INBOX = 0;
	public static final int ICON_SEND = 1;
	public static final int ICON_SETTINGS = 2;

	private PostOfficeStyle() {}

	private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, u, v, width, height, SHEET_SIZE, SHEET_SIZE);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y) {
		panel(graphics, x, y, PANEL_WIDTH);
	}

	/** The panel at any width from its own up: its two ends as drawn, the parchment and planks between drawn on. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width) {
		stretch(graphics, x, y, width, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 64);
	}

	/**
	 * Draws a piece of the sheet ({@code u, v}, {@code pieceWidth} wide) at another width: its left and right
	 * {@code ends} as they are, and the middle repeated across the gap.
	 */
	private static void stretch(GuiGraphicsExtractor graphics, int x, int y, int width, int u, int v, int pieceWidth, int height, int ends) {
		if (width <= pieceWidth) {
			blit(graphics, x, y, u, v, width, height);
			return;
		}
		blit(graphics, x, y, u, v, ends, height);
		blit(graphics, x + width - ends, y, u + pieceWidth - ends, v, ends, height);
		int middle = pieceWidth - 2 * ends;
		for (int dx = ends; dx < width - ends; dx += middle)
			blit(graphics, x + dx, y, u + ends, v, Math.min(middle, width - ends - dx), height);
	}

	/** An 18x18 slot frame; pass the slot's own position, the frame goes one pixel outside it. */
	public static void slot(GuiGraphicsExtractor graphics, Slot style, int slotX, int slotY) {
		blit(graphics, slotX - 1, slotY - 1, style.u, 0, 18, 18);
	}

	/** A postage-stamp tab sitting on the panel's top edge at panelY, with its icon. */
	public static void tab(GuiGraphicsExtractor graphics, int x, int panelY, int index, int icon, boolean active) {
		if (active) {
			blit(graphics, x, panelY - 26, 206, 18 + index * 28, TAB_WIDTH, TAB_ACTIVE_HEIGHT);
			blit(graphics, x + 7, panelY - 21, 176 + icon * 16, 104, 16, 16);
		} else {
			blit(graphics, x, panelY - 22, 176, 18 + index * 28, TAB_WIDTH, TAB_HEIGHT);
			blit(graphics, x + 7, panelY - 16, 176 + icon * 16, 104, 16, 16);
		}
	}

	/** The brass name plaque, centred on the panel, with its text engraved in the middle. */
	public static void plaque(GuiGraphicsExtractor graphics, Font font, int panelX, int panelY, Component text) {
		plaque(graphics, font, panelX, panelY, PANEL_WIDTH, text);
	}

	/** The brass name plaque, in the middle of the top of a panel this wide, stretched to fit the name. */
	public static void plaque(GuiGraphicsExtractor graphics, Font font, int panelX, int panelY, int panelWidth, Component text) {
		int width = plaqueWidth(font.width(text.getString()), panelWidth);
		int x = panelX + (panelWidth - width) / 2;
		stretch(graphics, x, panelY + 3, width, 100, 210, PLAQUE_WIDTH, PLAQUE_HEIGHT, 6);
		List<String> shown = clamp(text.getString(), width - 12, 1, font::width);
		String line = shown.isEmpty() ? "" : shown.get(0);
		graphics.text(font, line, x + (width - font.width(line)) / 2, panelY + 6, ENGRAVED, false);
	}

	/** How wide the plaque is for a name this wide: its own width at least, and no wider than leaves wood either side. */
	public static int plaqueWidth(int textWidth, int panelWidth) {
		return Math.max(PLAQUE_WIDTH, Math.min(textWidth + 16, panelWidth - 64));
	}

	public static void textField(GuiGraphicsExtractor graphics, int x, int y) {
		textField(graphics, x, y, TEXT_FIELD_WIDTH);
	}

	public static void textField(GuiGraphicsExtractor graphics, int x, int y, int width) {
		stretch(graphics, x, y, width, 0, 194, TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT, 4);
	}

	/** A ruled ledger page with a red margin, as used for lists of addresses. */
	public static void ledger(GuiGraphicsExtractor graphics, int x, int y) {
		ledger(graphics, x, y, LEDGER_WIDTH);
	}

	/** The ledger page at any width: its red margin as drawn, the ruled page carried on to the right. */
	public static void ledger(GuiGraphicsExtractor graphics, int x, int y, int width) {
		stretch(graphics, x, y, width, 0, 210, LEDGER_WIDTH, LEDGER_HEIGHT, 16);
	}

	public static void button(GuiGraphicsExtractor graphics, Font font, ButtonStyle style, int x, int y, Component label,
			boolean active, boolean hovered) {
		int v = !active ? style.disabledV : hovered ? style.hoverV : style.v;
		blit(graphics, x, y, 176, v, BUTTON_WIDTH, BUTTON_HEIGHT);
		int color = active ? style.textColor : 0xFF4E4636;
		graphics.text(font, label, x + (BUTTON_WIDTH - font.width(label)) / 2, y + 4, color, false);
	}

	// ---------------------------------------------------------------- the letter page sheet

	public static final Identifier PAGE_SHEET = PostRider.asResource("textures/gui/letter_page.png");
	public static final int PAGE_WIDTH = 148;
	public static final int PAGE_HEIGHT = 182;
	/** Where writing starts on the page, how wide it may run, and the line spacing of the ruled lines. */
	public static final int PAGE_TEXT_X = 18;
	public static final int PAGE_TEXT_Y = 20;
	public static final int PAGE_TEXT_WIDTH = 114;
	public static final int PAGE_LINE_HEIGHT = 9;
	public static final int PAGE_LINES = 14;
	public static final int ARROW_SIZE = 9;
	public static final int POSTMARK_SIZE = 41;

	public static void page(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, PAGE_SHEET, x, y, 0, 0, PAGE_WIDTH, PAGE_HEIGHT, SHEET_SIZE, SHEET_SIZE);
	}

	/** An inked page-turn arrow; previous points left, next points right. */
	public static void arrow(GuiGraphicsExtractor graphics, int x, int y, boolean next, boolean hovered) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, PAGE_SHEET, x, y, next ? 162 : 150, hovered ? 60 : 48, ARROW_SIZE, ARROW_SIZE, SHEET_SIZE, SHEET_SIZE);
	}

	/** The round Post Rider postmark, with the day it was posted in the middle. */
	public static void postmark(GuiGraphicsExtractor graphics, Font font, int x, int y, long day) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, PAGE_SHEET, x, y, 152, 2, POSTMARK_SIZE, POSTMARK_SIZE, SHEET_SIZE, SHEET_SIZE);
		Component dayText = Component.translatable("gui.postrider.letter.postmark_day", day);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + POSTMARK_SIZE / 2.0F, y + POSTMARK_SIZE / 2.0F - 2);
		graphics.pose().scale(0.75F, 0.75F);
		graphics.text(font, dayText, -font.width(dayText) / 2, 0, 0xFF3C5A8A, false);
		graphics.pose().popMatrix();
	}

	/** Writing on the parchment, without the drop shadow vanilla text has. */
	/** Text wrapped to a width, a word at a time (a word too long for a line is broken where it has to be). */
	public static List<String> wrap(String text, int width, ToIntFunction<String> widthOf) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String tried = line.isEmpty() ? word : line + " " + word;
			if (widthOf.applyAsInt(tried) <= width) {
				line.setLength(0);
				line.append(tried);
				continue;
			}
			if (!line.isEmpty())
				lines.add(line.toString());
			line.setLength(0);
			// A word wider than a whole line goes on as many lines as it takes
			while (widthOf.applyAsInt(word) > width) {
				int fits = 1;
				while (fits < word.length() && widthOf.applyAsInt(word.substring(0, fits + 1)) <= width)
					fits++;
				lines.add(word.substring(0, fits));
				word = word.substring(fits);
			}
			line.append(word);
		}
		if (!line.isEmpty())
			lines.add(line.toString());
		return lines;
	}

	/** Text wrapped to a width, at most this many lines; if there's more, the last line is cut short with "...". */
	public static List<String> clamp(String text, int width, int maxLines, ToIntFunction<String> widthOf) {
		List<String> lines = wrap(text, width, widthOf);
		if (lines.size() <= maxLines)
			return lines;
		List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
		String last = kept.get(maxLines - 1);
		while (!last.isEmpty() && widthOf.applyAsInt(last + "...") > width)
			last = last.substring(0, last.length() - 1);
		kept.set(maxLines - 1, last.stripTrailing() + "...");
		return kept;
	}

	public static void write(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}
}
