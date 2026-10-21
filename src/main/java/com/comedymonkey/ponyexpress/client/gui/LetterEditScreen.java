package com.comedymonkey.ponyexpress.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.comedymonkey.ponyexpress.content.letter.LetterDraft;
import com.comedymonkey.ponyexpress.content.letter.LetterText;
import com.comedymonkey.ponyexpress.network.EditLetterPayload;
import com.comedymonkey.ponyexpress.registry.ModDataComponents;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Writing a letter on a sheet of parchment: up to four pages, saved as a draft until it's sealed. */
public class LetterEditScreen extends Screen {
	private final int slot;
	private final List<String> pages = new ArrayList<>();
	private LetterText editor;
	private int page;
	private int frame;
	private boolean confirmingSeal;
	private boolean sent;
	private PostOfficeButton leftButton;
	private PostOfficeButton rightButton;

	public LetterEditScreen(Player player, InteractionHand hand, ItemStack letter) {
		super(Component.translatable("gui.ponyexpress.letter.write"));
		slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
		LetterDraft draft = letter.get(ModDataComponents.LETTER_DRAFT.get());
		if (draft != null)
			pages.addAll(draft.pages());
		if (pages.isEmpty())
			pages.add("");
	}

	private int pageX() {
		return (width - PostOfficeStyle.PAGE_WIDTH) / 2;
	}

	private int pageY() {
		return Math.max(4, (height - PostOfficeStyle.PAGE_HEIGHT - 24) / 2);
	}

	@Override
	protected void init() {
		editor = new LetterText(font::width, PostOfficeStyle.PAGE_TEXT_WIDTH, PostOfficeStyle.PAGE_LINES, LetterDraft.MAX_PAGE_LENGTH);
		editor.setText(pages.get(page));
		int buttonY = pageY() + PostOfficeStyle.PAGE_HEIGHT + 4;
		int centre = width / 2;
		leftButton = addRenderableWidget(new PostOfficeButton(centre - PostOfficeStyle.BUTTON_WIDTH - 4, buttonY,
			Component.empty(), PostOfficeStyle.ButtonStyle.BRASS, this::pressLeft));
		rightButton = addRenderableWidget(new PostOfficeButton(centre + 4, buttonY,
			Component.empty(), PostOfficeStyle.ButtonStyle.WAX, this::pressRight));
		updateButtons();
	}

	private void updateButtons() {
		leftButton.setMessage(Component.translatable(confirmingSeal ? "gui.ponyexpress.letter.back" : "gui.ponyexpress.letter.done"));
		rightButton.setMessage(Component.translatable(confirmingSeal ? "gui.ponyexpress.letter.seal_confirm" : "gui.ponyexpress.letter.seal"));
		rightButton.active = confirmingSeal || pages.stream().anyMatch(p -> !p.isBlank()) || !editor.getText().isBlank();
	}

	private void pressLeft() {
		if (confirmingSeal) {
			confirmingSeal = false;
			updateButtons();
		} else {
			onClose();
		}
	}

	private void pressRight() {
		if (!confirmingSeal) {
			confirmingSeal = true;
			updateButtons();
			return;
		}
		send(true);
		if (minecraft != null)
			minecraft.setScreen(null);
	}

	private void storePage() {
		pages.set(page, editor.getText());
	}

	private void turnTo(int newPage) {
		storePage();
		if (newPage < 0 || newPage >= LetterDraft.MAX_PAGES)
			return;
		while (pages.size() <= newPage)
			pages.add("");
		page = newPage;
		editor.setText(pages.get(page));
		updateButtons();
	}

	private void send(boolean seal) {
		if (sent)
			return;
		sent = true;
		storePage();
		PacketDistributor.sendToServer(new EditLetterPayload(slot, LetterDraft.sanitize(pages), seal));
	}

	@Override
	public void onClose() {
		send(false);
		super.onClose();
	}

	@Override
	public void tick() {
		frame++;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = pageX(), y = pageY();
		PostOfficeStyle.page(graphics, x, y);
		int textX = x + PostOfficeStyle.PAGE_TEXT_X, textY = y + PostOfficeStyle.PAGE_TEXT_Y;

		if (confirmingSeal) {
			graphics.drawWordWrap(font, Component.translatable("gui.ponyexpress.letter.seal_warning"), textX, textY + 40,
				PostOfficeStyle.PAGE_TEXT_WIDTH, PostOfficeStyle.INK);
			return;
		}
		List<LetterText.Line> lines = editor.lines();
		for (int i = 0; i < lines.size(); i++)
			graphics.drawString(font, editor.lineText(lines.get(i)), textX, textY + i * PostOfficeStyle.PAGE_LINE_HEIGHT, PostOfficeStyle.INK, false);
		if (frame / 6 % 2 == 0) {
			int cx = textX + editor.cursorX(), cy = textY + editor.cursorLine() * PostOfficeStyle.PAGE_LINE_HEIGHT;
			graphics.fill(cx, cy - 1, cx + 1, cy + 9, PostOfficeStyle.INK);
		}

		Component number = Component.translatable("gui.ponyexpress.letter.page", page + 1, pages.size());
		graphics.drawString(font, number, x + (PostOfficeStyle.PAGE_WIDTH - font.width(number)) / 2, y + 164, PostOfficeStyle.PENCIL, false);
		if (page > 0)
			PostOfficeStyle.arrow(graphics, prevArrowX(), arrowY(), false, overArrow(mouseX, mouseY, prevArrowX()));
		if (page < LetterDraft.MAX_PAGES - 1)
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

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!confirmingSeal && button == 0) {
			if (page > 0 && overArrow(mouseX, mouseY, prevArrowX())) {
				turnTo(page - 1);
				return true;
			}
			if (page < LetterDraft.MAX_PAGES - 1 && overArrow(mouseX, mouseY, nextArrowX())) {
				turnTo(page + 1);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (confirmingSeal)
			return false;
		type(String.valueOf(codePoint));
		return true;
	}

	/** Types at the cursor; if the page is full, carries on onto the next page. */
	private void type(String text) {
		if (!editor.insert(text) && page < LetterDraft.MAX_PAGES - 1 && editor.getCursor() == editor.getText().length()) {
			turnTo(page + 1);
			editor.insert(text.startsWith("\n") ? text.substring(1) : text);
		}
		updateButtons();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (super.keyPressed(keyCode, scanCode, modifiers))
			return true;
		if (confirmingSeal)
			return false;
		if (Screen.isPaste(keyCode) && minecraft != null) {
			type(minecraft.keyboardHandler.getClipboard());
			return true;
		}
		switch (keyCode) {
			case 259 -> editor.backspace();
			case 261 -> editor.delete();
			case 257, 335 -> type("\n");
			case 263 -> editor.left();
			case 262 -> editor.right();
			case 265 -> editor.up();
			case 264 -> editor.down();
			case 268 -> editor.home();
			case 269 -> editor.end();
			case 266 -> turnTo(page - 1);
			case 267 -> turnTo(page + 1);
			default -> {
				return false;
			}
		}
		frame = 0;
		updateButtons();
		return true;
	}
}
