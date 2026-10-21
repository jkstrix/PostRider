package com.comedymonkey.ponyexpress.client.gui;

import java.util.List;
import java.util.Optional;

import com.comedymonkey.ponyexpress.content.letter.LetterContent;
import com.comedymonkey.ponyexpress.registry.ModDataComponents;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Reading a sealed letter: its pages, the writer's signature and, once delivered, its postmark. */
public class LetterReadScreen extends Screen {
	private final LetterContent letter;
	private int page;

	public LetterReadScreen(ItemStack stack) {
		super(Component.translatable("gui.ponyexpress.letter.read"));
		LetterContent content = stack.get(ModDataComponents.LETTER.get());
		letter = content != null ? content : new LetterContent(List.of(), "", Optional.empty());
	}

	private int pageCount() {
		return Math.max(1, letter.pages().size());
	}

	private int pageX() {
		return (width - PostOfficeStyle.PAGE_WIDTH) / 2;
	}

	private int pageY() {
		return Math.max(4, (height - PostOfficeStyle.PAGE_HEIGHT - 24) / 2);
	}

	@Override
	protected void init() {
		addRenderableWidget(new PostOfficeButton((width - PostOfficeStyle.BUTTON_WIDTH) / 2, pageY() + PostOfficeStyle.PAGE_HEIGHT + 4,
			Component.translatable("gui.ponyexpress.letter.done"), PostOfficeStyle.ButtonStyle.BRASS, this::onClose));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = pageX(), y = pageY();
		PostOfficeStyle.page(graphics, x, y);
		int textX = x + PostOfficeStyle.PAGE_TEXT_X, textY = y + PostOfficeStyle.PAGE_TEXT_Y;

		if (page == 0)
			letter.postmark().ifPresent(mark -> {
				PostOfficeStyle.postmark(graphics, font, x + PostOfficeStyle.PAGE_WIDTH - PostOfficeStyle.POSTMARK_SIZE - 6, y + 4, mark.day());
				graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.ponyexpress.letter.posted_at", mark.from()).getString(), 92),
					textX, y + 8, PostOfficeStyle.PENCIL, false);
			});

		String text = page < letter.pages().size() ? letter.pages().get(page) : "";
		int line = 0;
		for (String paragraph : text.split("\n", -1)) {
			List<FormattedCharSequence> wrapped = font.split(Component.literal(paragraph), PostOfficeStyle.PAGE_TEXT_WIDTH);
			if (wrapped.isEmpty())
				line++;
			for (FormattedCharSequence part : wrapped) {
				if (line >= PostOfficeStyle.PAGE_LINES)
					break;
				graphics.drawString(font, part, textX, textY + line * PostOfficeStyle.PAGE_LINE_HEIGHT, PostOfficeStyle.INK, false);
				line++;
			}
		}

		if (page == pageCount() - 1 && !letter.author().isEmpty()) {
			Component signature = Component.translatable("gui.ponyexpress.letter.signed", letter.author());
			graphics.drawString(font, signature, x + PostOfficeStyle.PAGE_WIDTH - 18 - font.width(signature), y + 150, PostOfficeStyle.INK, false);
		}

		Component number = Component.translatable("gui.ponyexpress.letter.page", page + 1, pageCount());
		graphics.drawString(font, number, x + (PostOfficeStyle.PAGE_WIDTH - font.width(number)) / 2, y + 164, PostOfficeStyle.PENCIL, false);
		if (page > 0)
			PostOfficeStyle.arrow(graphics, prevArrowX(), arrowY(), false, overArrow(mouseX, mouseY, prevArrowX()));
		if (page < pageCount() - 1)
			PostOfficeStyle.arrow(graphics, nextArrowX(), arrowY(), true, overArrow(mouseX, mouseY, nextArrowX()));
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
	}

	private int prevArrowX() {
		return pageX() + 18;
	}

	private int nextArrowX() {
		return pageX() + PostOfficeStyle.PAGE_WIDTH - 18 - PostOfficeStyle.ARROW_SIZE;
	}

	private int arrowY() {
		return pageY() + 163;
	}

	private boolean overArrow(double mouseX, double mouseY, int arrowX) {
		return mouseX >= arrowX - 2 && mouseX < arrowX + PostOfficeStyle.ARROW_SIZE + 2
			&& mouseY >= arrowY() - 2 && mouseY < arrowY() + PostOfficeStyle.ARROW_SIZE + 2;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && page > 0 && overArrow(mouseX, mouseY, prevArrowX())) {
			page--;
			return true;
		}
		if (button == 0 && page < pageCount() - 1 && overArrow(mouseX, mouseY, nextArrowX())) {
			page++;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 266 || keyCode == 263) {
			page = Math.max(0, page - 1);
			return true;
		}
		if (keyCode == 267 || keyCode == 262) {
			page = Math.min(pageCount() - 1, page + 1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
