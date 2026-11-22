package com.comedymonkey.postrider.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A brass or red-wax button drawn from the post office sheet. */
public class PostOfficeButton extends AbstractButton {
	private final PostOfficeStyle.ButtonStyle style;
	private final Runnable onPress;
	/**
	 * The label as given: vanilla greys out a disabled button's message with a colour of its own, where these buttons
	 * draw it in their own faded ink.
	 */
	private Component label;

	public PostOfficeButton(int x, int y, Component label, PostOfficeStyle.ButtonStyle style, Runnable onPress) {
		super(x, y, PostOfficeStyle.BUTTON_WIDTH, PostOfficeStyle.BUTTON_HEIGHT, label);
		this.style = style;
		this.onPress = onPress;
		this.label = label;
	}

	@Override
	public void setMessage(Component message) {
		super.setMessage(message);
		this.label = message;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		PostOfficeStyle.button(graphics, Minecraft.getInstance().font, style, getX(), getY(), label, active, isHoveredOrFocused());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
