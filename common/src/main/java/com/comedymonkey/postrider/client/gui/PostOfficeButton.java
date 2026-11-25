package com.comedymonkey.postrider.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A brass or red-wax button drawn from the post office sheet. */
public class PostOfficeButton extends AbstractButton {
	private final PostOfficeStyle.ButtonStyle style;
	private final Runnable onPress;

	public PostOfficeButton(int x, int y, Component label, PostOfficeStyle.ButtonStyle style, Runnable onPress) {
		super(x, y, PostOfficeStyle.BUTTON_WIDTH, PostOfficeStyle.BUTTON_HEIGHT, label);
		this.style = style;
		this.onPress = onPress;
	}

	@Override
	public void onPress() {
		onPress.run();
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		PostOfficeStyle.button(graphics, Minecraft.getInstance().font, style, getX(), getY(), getMessage(), active, isHoveredOrFocused());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
