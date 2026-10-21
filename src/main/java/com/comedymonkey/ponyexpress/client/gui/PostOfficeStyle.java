package com.comedymonkey.ponyexpress.client.gui;

import com.comedymonkey.ponyexpress.PonyExpress;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The look shared by every Pony Express screen: walnut panelling, a parchment work surface, brass fittings and
 * postage-stamp tabs, all drawn from one sheet (textures/gui/post_office.png) so the screens stay consistent.
 */
public final class PostOfficeStyle {
	public static final ResourceLocation SHEET = PonyExpress.asResource("textures/gui/post_office.png");
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

	private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
		graphics.blit(SHEET, x, y, u, v, width, height, SHEET_SIZE, SHEET_SIZE);
	}

	public static void panel(GuiGraphics graphics, int x, int y) {
		blit(graphics, x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
	}

	/** An 18x18 slot frame; pass the slot's own position, the frame goes one pixel outside it. */
	public static void slot(GuiGraphics graphics, Slot style, int slotX, int slotY) {
		blit(graphics, slotX - 1, slotY - 1, style.u, 0, 18, 18);
	}

	/** A postage-stamp tab sitting on the panel's top edge at panelY, with its icon. */
	public static void tab(GuiGraphics graphics, int x, int panelY, int index, int icon, boolean active) {
		if (active) {
			blit(graphics, x, panelY - 26, 206, 18 + index * 28, TAB_WIDTH, TAB_ACTIVE_HEIGHT);
			blit(graphics, x + 7, panelY - 21, 176 + icon * 16, 104, 16, 16);
		} else {
			blit(graphics, x, panelY - 22, 176, 18 + index * 28, TAB_WIDTH, TAB_HEIGHT);
			blit(graphics, x + 7, panelY - 16, 176 + icon * 16, 104, 16, 16);
		}
	}

	/** The brass name plaque, centred on the panel, with its text engraved in the middle. */
	public static void plaque(GuiGraphics graphics, Font font, int panelX, int panelY, Component text) {
		int x = panelX + (PANEL_WIDTH - PLAQUE_WIDTH) / 2;
		blit(graphics, x, panelY + 3, 100, 210, PLAQUE_WIDTH, PLAQUE_HEIGHT);
		String shown = font.plainSubstrByWidth(text.getString(), PLAQUE_WIDTH - 12);
		graphics.drawString(font, shown, x + (PLAQUE_WIDTH - font.width(shown)) / 2, panelY + 6, ENGRAVED, false);
	}

	public static void textField(GuiGraphics graphics, int x, int y) {
		blit(graphics, x, y, 0, 194, TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT);
	}

	/** A ruled ledger page with a red margin, as used for lists of addresses. */
	public static void ledger(GuiGraphics graphics, int x, int y) {
		blit(graphics, x, y, 0, 210, LEDGER_WIDTH, LEDGER_HEIGHT);
	}

	public static void button(GuiGraphics graphics, Font font, ButtonStyle style, int x, int y, Component label,
			boolean active, boolean hovered) {
		int v = !active ? style.disabledV : hovered ? style.hoverV : style.v;
		blit(graphics, x, y, 176, v, BUTTON_WIDTH, BUTTON_HEIGHT);
		int color = active ? style.textColor : 0xFF4E4636;
		graphics.drawString(font, label, x + (BUTTON_WIDTH - font.width(label)) / 2, y + 4, color, false);
	}

	// ---------------------------------------------------------------- the letter page sheet

	public static final ResourceLocation PAGE_SHEET = PonyExpress.asResource("textures/gui/letter_page.png");
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

	public static void page(GuiGraphics graphics, int x, int y) {
		graphics.blit(PAGE_SHEET, x, y, 0, 0, PAGE_WIDTH, PAGE_HEIGHT, SHEET_SIZE, SHEET_SIZE);
	}

	/** An inked page-turn arrow; previous points left, next points right. */
	public static void arrow(GuiGraphics graphics, int x, int y, boolean next, boolean hovered) {
		graphics.blit(PAGE_SHEET, x, y, next ? 162 : 150, hovered ? 60 : 48, ARROW_SIZE, ARROW_SIZE, SHEET_SIZE, SHEET_SIZE);
	}

	/** The round Pony Express postmark, with the day it was posted in the middle. */
	public static void postmark(GuiGraphics graphics, Font font, int x, int y, long day) {
		graphics.blit(PAGE_SHEET, x, y, 152, 2, POSTMARK_SIZE, POSTMARK_SIZE, SHEET_SIZE, SHEET_SIZE);
		Component dayText = Component.translatable("gui.ponyexpress.letter.postmark_day", day);
		graphics.pose().pushPose();
		graphics.pose().translate(x + POSTMARK_SIZE / 2.0F, y + POSTMARK_SIZE / 2.0F - 2, 0);
		graphics.pose().scale(0.75F, 0.75F, 1);
		graphics.drawString(font, dayText, -font.width(dayText) / 2, 0, 0xFF3C5A8A, false);
		graphics.pose().popPose();
	}

	/** Writing on the parchment, without the drop shadow vanilla text has. */
	public static void write(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}
}
