package com.comedymonkey.postrider.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A one-line text box written in ink on the parchment: vanilla's typing and cursor keys, but drawn without the drop
 * shadow vanilla text boxes always have, which blurs dark ink. Meant for short text that fits without scrolling.
 */
public class InkTextField extends EditBox {
	private final Font font;

	public InkTextField(Font font, int x, int y, int width, int height, Component message) {
		super(font, x, y, width, height, message);
		this.font = font;
		setBordered(false);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (!isVisible())
			return;
		String text = font.plainSubstrByWidth(getValue(), getWidth());
		graphics.text(font, text, getX(), getY(), PostOfficeStyle.INK, false);
		if (isFocused() && Util.getMillis() / 300 % 2 == 0) {
			int cursor = Math.min(getCursorPosition(), text.length());
			int cx = getX() + font.width(text.substring(0, cursor));
			graphics.fill(cx, getY() - 1, cx + 1, getY() + font.lineHeight - 1, PostOfficeStyle.INK);
		}
	}
}
