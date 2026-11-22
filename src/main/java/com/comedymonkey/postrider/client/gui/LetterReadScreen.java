package com.comedymonkey.postrider.client.gui;

import java.util.List;
import java.util.Optional;

import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.registry.ModDataComponents;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Reading a sealed letter: its pages, the writer's signature and, once delivered, its postmark. */
public class LetterReadScreen extends Screen {
	private final LetterContent letter;
	private int page;

	public LetterReadScreen(ItemStack stack) {
		this(contentOf(stack), 0);
	}

	/** A letter open at a page: for Dailies to draw in playback, as a recording has it (client/dailies). */
	public LetterReadScreen(LetterContent letter, int page) {
		super(Component.translatable("gui.postrider.letter.read"));
		this.letter = letter;
		this.page = Math.max(0, Math.min(page, Math.max(1, letter.pages().size()) - 1));
	}

	private static LetterContent contentOf(ItemStack stack) {
		LetterContent content = stack.get(ModDataComponents.LETTER.get());
		return content != null ? content : new LetterContent(List.of(), "", Optional.empty());
	}

	/** The letter being read; for Dailies to record. */
	public LetterContent letter() {
		return letter;
	}

	/** The page open, from 0; for Dailies to record. */
	public int page() {
		return page;
	}

	private int pageCount() {
		return Math.max(1, letter.pages().size());
	}

	/** Where the page starts on the screen; Dailies keeps the pointer from here. */
	public int pageX() {
		return (width - PostOfficeStyle.PAGE_WIDTH) / 2;
	}

	public int pageY() {
		return Math.max(4, (height - PostOfficeStyle.PAGE_HEIGHT - 24) / 2);
	}

	@Override
	protected void init() {
		addRenderableWidget(new PostOfficeButton((width - PostOfficeStyle.BUTTON_WIDTH) / 2, pageY() + PostOfficeStyle.PAGE_HEIGHT + 4,
			Component.translatable("gui.postrider.letter.done"), PostOfficeStyle.ButtonStyle.BRASS, this::onClose));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int x = pageX(), y = pageY();
		PostOfficeStyle.page(graphics, x, y);
		int textX = x + PostOfficeStyle.PAGE_TEXT_X, textY = y + PostOfficeStyle.PAGE_TEXT_Y;

		if (page == 0)
			letter.postmark().ifPresent(mark -> {
				PostOfficeStyle.postmark(graphics, font, x + PostOfficeStyle.PAGE_WIDTH - PostOfficeStyle.POSTMARK_SIZE - 6, y + 4, mark.day());
				graphics.text(font, font.plainSubstrByWidth(Component.translatable("gui.postrider.letter.posted_at", mark.from()).getString(), 92),
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
				graphics.text(font, part, textX, textY + line * PostOfficeStyle.PAGE_LINE_HEIGHT, PostOfficeStyle.INK, false);
				line++;
			}
		}

		if (page == pageCount() - 1 && !letter.author().isEmpty()) {
			Component signature = Component.translatable("gui.postrider.letter.signed", letter.author());
			graphics.text(font, signature, x + PostOfficeStyle.PAGE_WIDTH - 18 - font.width(signature), y + 150, PostOfficeStyle.INK, false);
		}

		Component number = Component.translatable("gui.postrider.letter.page", page + 1, pageCount());
		graphics.text(font, number, x + (PostOfficeStyle.PAGE_WIDTH - font.width(number)) / 2, y + 164, PostOfficeStyle.PENCIL, false);
		if (page > 0)
			PostOfficeStyle.arrow(graphics, prevArrowX(), arrowY(), false, overArrow(mouseX, mouseY, prevArrowX()));
		if (page < pageCount() - 1)
			PostOfficeStyle.arrow(graphics, nextArrowX(), arrowY(), true, overArrow(mouseX, mouseY, nextArrowX()));
	}

	/** Over the world, dimmed, as vanilla's books are, rather than on the menu background. */
	@Override
	public boolean isInGameUi() {
		return true;
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = event.button();
		if (button == InputConstants.MOUSE_BUTTON_LEFT && page > 0 && overArrow(mouseX, mouseY, prevArrowX())) {
			page--;
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT && page < pageCount() - 1 && overArrow(mouseX, mouseY, nextArrowX())) {
			page++;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.input();
		if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_LEFT) {
			page = Math.max(0, page - 1);
			return true;
		}
		if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_RIGHT) {
			page = Math.min(pageCount() - 1, page + 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
