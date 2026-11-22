package com.comedymonkey.postrider.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.comedymonkey.postrider.content.letter.LetterDraft;
import com.comedymonkey.postrider.content.letter.LetterText;
import com.comedymonkey.postrider.network.EditLetterPayload;
import com.comedymonkey.postrider.registry.ModDataComponents;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Writing a letter on a sheet of parchment: up to four pages, saved as a draft until it's sealed. */
public class LetterEditScreen extends Screen {
	private final int slot;
	private final List<String> pages = new ArrayList<>();
	private LetterText editor;
	private int page;
	private int frame;
	private boolean confirmingSeal;
	private boolean sent;
	/** Drawn by Dailies in playback: shows the letter as it stood and never sends anything. */
	private final boolean readOnly;
	/** Where the cursor starts on the open page, for a read-only copy; -1 at the end. */
	private int startCursor = -1;
	private PostOfficeButton leftButton;
	private PostOfficeButton rightButton;

	public LetterEditScreen(Player player, InteractionHand hand, ItemStack letter) {
		super(Component.translatable("gui.postrider.letter.write"));
		slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
		readOnly = false;
		LetterDraft draft = letter.get(ModDataComponents.LETTER_DRAFT.get());
		if (draft != null)
			pages.addAll(draft.pages());
		if (pages.isEmpty())
			pages.add("");
	}

	private LetterEditScreen(List<String> written, int page, int cursor, boolean confirmingSeal) {
		super(Component.translatable("gui.postrider.letter.write"));
		slot = -1;
		readOnly = true;
		pages.addAll(written);
		if (pages.isEmpty())
			pages.add("");
		this.page = Math.max(0, Math.min(page, pages.size() - 1));
		this.startCursor = cursor;
		this.confirmingSeal = confirmingSeal;
	}

	/**
	 * The letter as a recording has it at one moment, for Dailies to draw in playback (client/dailies): its pages, the
	 * page open, where the cursor is on it, and whether the seal is being confirmed. It sends nothing, ever.
	 */
	public static LetterEditScreen readOnly(List<String> pages, int page, int cursor, boolean confirmingSeal) {
		return new LetterEditScreen(pages, page, cursor, confirmingSeal);
	}

	/** Every page as written so far, the open one as it stands; for Dailies to record. */
	public List<String> pagesNow() {
		List<String> now = new ArrayList<>(pages);
		if (editor != null)
			now.set(page, editor.getText());
		return now;
	}

	/** The page open, from 0; for Dailies to record. */
	public int page() {
		return page;
	}

	/** Where the cursor is on the open page; for Dailies to record. */
	public int cursor() {
		return editor == null ? 0 : editor.getCursor();
	}

	/** Whether the seal is being confirmed; for Dailies to record. */
	public boolean confirmingSeal() {
		return confirmingSeal;
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
		// Typed characters only come while the window takes text input, which is asked for (a text box asks when it's
		// focused; the whole page takes typing here). A read-only copy takes none.
		if (!readOnly)
			minecraft.textInputManager().startTextInput(this);
		editor = new LetterText(font::width, PostOfficeStyle.PAGE_TEXT_WIDTH, PostOfficeStyle.PAGE_LINES, LetterDraft.MAX_PAGE_LENGTH);
		editor.setText(pages.get(page));
		// The text puts the cursor at its end; a read-only copy moves it back to where it was recorded
		for (int back = startCursor < 0 ? 0 : editor.getCursor() - startCursor; back > 0; back--)
			editor.left();
		int buttonY = pageY() + PostOfficeStyle.PAGE_HEIGHT + 4;
		int centre = width / 2;
		leftButton = addRenderableWidget(new PostOfficeButton(centre - PostOfficeStyle.BUTTON_WIDTH - 4, buttonY,
			Component.empty(), PostOfficeStyle.ButtonStyle.BRASS, this::pressLeft));
		rightButton = addRenderableWidget(new PostOfficeButton(centre + 4, buttonY,
			Component.empty(), PostOfficeStyle.ButtonStyle.WAX, this::pressRight));
		updateButtons();
	}

	private void updateButtons() {
		leftButton.setMessage(Component.translatable(confirmingSeal ? "gui.postrider.letter.back" : "gui.postrider.letter.done"));
		rightButton.setMessage(Component.translatable(confirmingSeal ? "gui.postrider.letter.seal_confirm" : "gui.postrider.letter.seal"));
		rightButton.active = confirmingSeal || pages.stream().anyMatch(p -> !p.isBlank()) || !editor.getText().isBlank();
	}

	private void pressLeft() {
		// Let go of the keyboard, or the next space or Enter would press this button again
		setFocused(null);
		if (confirmingSeal) {
			confirmingSeal = false;
			updateButtons();
		} else {
			onClose();
		}
	}

	private void pressRight() {
		// Let go of the keyboard, or the next space or Enter would press this button again
		setFocused(null);
		if (!confirmingSeal) {
			confirmingSeal = true;
			updateButtons();
			return;
		}
		send(true);
		if (minecraft != null)
			minecraft.gui.setScreen(null);
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
		if (sent || readOnly)
			return;
		sent = true;
		storePage();
		ClientPacketDistributor.sendToServer(new EditLetterPayload(slot, LetterDraft.sanitize(pages), seal));
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
	public void removed() {
		minecraft.textInputManager().stopTextInput(this);
		super.removed();
	}

	/** Keys are for writing, so none of them opens another screen over the letter. */
	@Override
	public boolean isInputCaptured() {
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int x = pageX(), y = pageY();
		PostOfficeStyle.page(graphics, x, y);
		int textX = x + PostOfficeStyle.PAGE_TEXT_X, textY = y + PostOfficeStyle.PAGE_TEXT_Y;

		if (confirmingSeal) {
			graphics.textWithWordWrap(font, Component.translatable("gui.postrider.letter.seal_warning"), textX, textY + 40,
				PostOfficeStyle.PAGE_TEXT_WIDTH, PostOfficeStyle.INK, false);
			return;
		}
		List<LetterText.Line> lines = editor.lines();
		for (int i = 0; i < lines.size(); i++)
			graphics.text(font, editor.lineText(lines.get(i)), textX, textY + i * PostOfficeStyle.PAGE_LINE_HEIGHT, PostOfficeStyle.INK, false);
		if (frame / 6 % 2 == 0) {
			int cx = textX + editor.cursorX(), cy = textY + editor.cursorLine() * PostOfficeStyle.PAGE_LINE_HEIGHT;
			graphics.fill(cx, cy - 1, cx + 1, cy + 9, PostOfficeStyle.INK);
		}

		Component number = Component.translatable("gui.postrider.letter.page", page + 1, pages.size());
		graphics.text(font, number, x + (PostOfficeStyle.PAGE_WIDTH - font.width(number)) / 2, y + 164, PostOfficeStyle.PENCIL, false);
		if (page > 0)
			PostOfficeStyle.arrow(graphics, prevArrowX(), arrowY(), false, overArrow(mouseX, mouseY, prevArrowX()));
		if (page < LetterDraft.MAX_PAGES - 1)
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

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		if (!confirmingSeal && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (page > 0 && overArrow(mouseX, mouseY, prevArrowX())) {
				turnTo(page - 1);
				return true;
			}
			if (page < LetterDraft.MAX_PAGES - 1 && overArrow(mouseX, mouseY, nextArrowX())) {
				turnTo(page + 1);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (confirmingSeal)
			return false;
		type(event.codepointAsString());
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
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event))
			return true;
		if (confirmingSeal)
			return false;
		if (event.isPaste() && minecraft != null) {
			type(minecraft.keyboardHandler.getClipboard());
			return true;
		}
		switch (event.input()) {
			case InputConstants.KEY_BACKSPACE -> editor.backspace();
			case InputConstants.KEY_DELETE -> editor.delete();
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> type("\n");
			case InputConstants.KEY_LEFT -> editor.left();
			case InputConstants.KEY_RIGHT -> editor.right();
			case InputConstants.KEY_UP -> editor.up();
			case InputConstants.KEY_DOWN -> editor.down();
			case InputConstants.KEY_HOME -> editor.home();
			case InputConstants.KEY_END -> editor.end();
			case InputConstants.KEY_PAGEUP -> turnTo(page - 1);
			case InputConstants.KEY_PAGEDOWN -> turnTo(page + 1);
			default -> {
				return false;
			}
		}
		frame = 0;
		updateButtons();
		return true;
	}
}
