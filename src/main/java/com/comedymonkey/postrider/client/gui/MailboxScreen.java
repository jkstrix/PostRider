package com.comedymonkey.postrider.client.gui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.mailbox.MailboxInfoCache;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.network.RenameMailboxPayload;
import com.comedymonkey.postrider.network.SendParcelPayload;
import com.comedymonkey.postrider.post.MailboxNames;
import com.comedymonkey.postrider.platform.Services;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The mailbox's three tabs: Inbox, Send and Settings, dressed as an old post office counter. */
public class MailboxScreen extends AbstractContainerScreen<MailboxMenu> {
	private static final int[] TAB_ICONS = {PostOfficeStyle.ICON_INBOX, PostOfficeStyle.ICON_SEND, PostOfficeStyle.ICON_SETTINGS};
	private static final String[] TAB_KEYS = {"inbox", "send", "settings"};
	/**
	 * On the Settings tab, under the Save button: the address rules, or in their place, two lines for what's wrong
	 * (or that it's saved).
	 */
	public static final int RULES_Y = 66;
	public static final int STATUS_LINES = 2;
	public static final int RULES_WIDTH = MailboxMenu.WIDTH - 16;
	/**
	 * On the Send tab, two lines under the ledger for how sending went (in place of the "To:" line, as the address
	 * is highlighted in the ledger), ending on the parchment.
	 */
	public static final int SEND_NOTICE_Y = 68;
	private static final int LEDGER_X = 68;
	/** The ledger of addresses fills the panel to the right of the parcel slots. */
	private static final int LEDGER_WIDTH = MailboxMenu.WIDTH - LEDGER_X - 8;
	public static final int SEND_NOTICE_WIDTH = LEDGER_WIDTH;
	public static final int SEND_NOTICE_LINES = 2;
	/** Where the parchment ends and the wood begins: text has to stay above it. */
	public static final int PARCHMENT_BOTTOM = 92;
	/** On the Settings tab, the owner's name beside the Save button, as far as the edge of the parchment. */
	private static final int OWNER_X = 70;
	private static final int OWNER_WIDTH = MailboxMenu.WIDTH - OWNER_X - 8;
	private static final int TOOLTIP_WIDTH = 200;
	/** The address box runs across the panel. */
	private static final int FIELD_WIDTH = MailboxMenu.WIDTH - 16;
	private static final int LEDGER_Y = 19;
	private static final int LEDGER_ROWS = PostOfficeStyle.LEDGER_HEIGHT / PostOfficeStyle.LEDGER_ROW;

	private InkTextField nameBox;
	private PostOfficeButton saveButton;
	private PostOfficeButton sendButton;
	private int seenInfoVersion = -1;
	private String name;
	private String ownerName = "";
	private List<String> addresses = List.of();
	@Nullable
	private String selected;
	private int scroll;
	/** A message drawn cut short to fit, and where (relative to the panel): hovering there shows all of it. */
	@Nullable
	private Component cutShort;
	private int cutX, cutY, cutWidth, cutHeight;

	public MailboxScreen(MailboxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, MailboxMenu.WIDTH, MailboxMenu.HEIGHT);
		name = menu.getOpenedName();
	}

	@Override
	protected void init() {
		super.init();
		nameBox = new InkTextField(font, leftPos + 11, topPos + 32, FIELD_WIDTH - 10, 9, Component.translatable("gui.postrider.mailbox.address"));
		nameBox.setMaxLength(MailboxNames.MAX_LENGTH);
		nameBox.setValue(hasName() ? name : "");
		addRenderableWidget(nameBox);
		saveButton = addRenderableWidget(new PostOfficeButton(leftPos + 8, topPos + 48,
			Component.translatable("gui.postrider.mailbox.save"), PostOfficeStyle.ButtonStyle.BRASS, this::save));
		sendButton = addRenderableWidget(new PostOfficeButton(leftPos + 7, topPos + 75,
			Component.translatable("gui.postrider.mailbox.send"), PostOfficeStyle.ButtonStyle.WAX, this::send));
		refreshInfo();
		updateWidgets();
	}

	private boolean hasName() {
		return !name.isEmpty();
	}

	private void refreshInfo() {
		MailboxInfoCache.Info info = MailboxInfoCache.get(menu.getPos());
		if (info == null || MailboxInfoCache.version() == seenInfoVersion)
			return;
		seenInfoVersion = MailboxInfoCache.version();
		boolean renamed = !info.name().equals(name);
		name = info.name();
		ownerName = info.ownerName();
		addresses = info.addresses();
		if (selected != null && !addresses.contains(selected))
			selected = null;
		scroll = Mth.clamp(scroll, 0, Math.max(0, addresses.size() - LEDGER_ROWS));
		if (renamed && nameBox != null && !nameBox.isFocused())
			nameBox.setValue(name);
	}

	/** Opens a tab, as clicking it does: the server is told too (the display test of Post Rider's Dailies compat uses it). */
	public void switchTab(int tab) {
		if (tab == menu.getTab())
			return;
		menu.setTab(tab);
		if (minecraft != null && minecraft.gameMode != null)
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
		updateWidgets();
	}

	private void updateWidgets() {
		int tab = menu.getTab();
		nameBox.visible = tab == MailboxMenu.TAB_SETTINGS;
		if (!nameBox.visible)
			nameBox.setFocused(false);
		saveButton.visible = tab == MailboxMenu.TAB_SETTINGS;
		saveButton.active = canSave();
		sendButton.visible = tab == MailboxMenu.TAB_SEND;
		sendButton.active = hasName() && selected != null && parcelHasItems();
	}

	private boolean parcelHasItems() {
		for (int i = MailboxMenu.PARCEL_START; i < MailboxMenu.PLAYER_START; i++)
			if (menu.slots.get(i).hasItem())
				return true;
		return false;
	}

	private int inboxCount() {
		int count = 0;
		for (int i = MailboxMenu.INBOX_START; i < MailboxMenu.PARCEL_START; i++)
			if (menu.slots.get(i).hasItem())
				count++;
		return count;
	}

	private void save() {
		Services.PLATFORM.sendToServer(new RenameMailboxPayload(nameBox.getValue()));
		nameBox.setFocused(false);
	}

	/**
	 * What's wrong with the address being typed, shown on the parchment as you type (the server checks again, but its
	 * messages would only show on the hotbar, hidden behind this screen); null if it can be saved or is unchanged.
	 */
	@Nullable
	private Component nameProblem() {
		String cleaned = MailboxNames.clean(nameBox.getValue());
		if (cleaned.isEmpty() || MailboxNames.key(cleaned).equals(MailboxNames.key(name)))
			return null;
		MailboxNames.Problem problem = MailboxNames.check(cleaned);
		if (problem != null)
			return MailboxNames.describe(problem);
		if (MailboxNames.isTaken(cleaned, addresses))
			return Component.translatable("message.postrider.mailbox.name_taken", cleaned);
		return null;
	}

	private boolean canSave() {
		String cleaned = MailboxNames.clean(nameBox.getValue());
		return MailboxNames.check(cleaned) == null && !MailboxNames.key(cleaned).equals(MailboxNames.key(name)) && nameProblem() == null;
	}

	private void send() {
		if (selected != null)
			Services.PLATFORM.sendToServer(new SendParcelPayload(selected));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshInfo();
		updateWidgets();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		for (int k = 0; k < MailboxMenu.TAB_COUNT; k++)
			if (k != menu.getTab())
				PostOfficeStyle.tab(graphics, tabX(k), topPos, k, TAB_ICONS[k], false);
		PostOfficeStyle.panel(graphics, leftPos, topPos, imageWidth);
		PostOfficeStyle.tab(graphics, tabX(menu.getTab()), topPos, menu.getTab(), TAB_ICONS[menu.getTab()], true);

		for (Slot slot : menu.slots) {
			if (!slot.isActive())
				continue;
			int index = slot.index;
			PostOfficeStyle.Slot style = index < MailboxMenu.PARCEL_START ? PostOfficeStyle.Slot.PIGEONHOLE
				: index < MailboxMenu.PLAYER_START ? PostOfficeStyle.Slot.KRAFT : PostOfficeStyle.Slot.BRASS;
			PostOfficeStyle.slot(graphics, style, leftPos + slot.x, topPos + slot.y);
		}
		if (menu.getTab() == MailboxMenu.TAB_SEND)
			renderLedger(graphics, mouseX, mouseY);
		if (menu.getTab() == MailboxMenu.TAB_SETTINGS)
			PostOfficeStyle.textField(graphics, leftPos + 8, topPos + 30, FIELD_WIDTH);
	}

	private void renderLedger(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = leftPos + LEDGER_X, y = topPos + LEDGER_Y;
		PostOfficeStyle.ledger(graphics, x, y, LEDGER_WIDTH);
		if (!hasName()) {
			graphics.textWithWordWrap(font, Component.translatable("gui.postrider.mailbox.name_first"), x + 12, y + 3, LEDGER_WIDTH - 16, PostOfficeStyle.INK_ERROR, false);
			return;
		}
		if (addresses.isEmpty()) {
			graphics.textWithWordWrap(font, Component.translatable("gui.postrider.mailbox.no_addresses"), x + 12, y + 3, LEDGER_WIDTH - 16, PostOfficeStyle.PENCIL, false);
			return;
		}
		int hovered = ledgerRowAt(mouseX, mouseY);
		for (int row = 0; row < LEDGER_ROWS && scroll + row < addresses.size(); row++) {
			String address = addresses.get(scroll + row);
			int rowY = y + 1 + row * PostOfficeStyle.LEDGER_ROW;
			if (address.equals(selected))
				graphics.fill(x + 1, rowY, x + LEDGER_WIDTH - 1, rowY + PostOfficeStyle.LEDGER_ROW, PostOfficeStyle.LEDGER_SELECTED);
			else if (row == hovered)
				graphics.fill(x + 1, rowY, x + LEDGER_WIDTH - 1, rowY + PostOfficeStyle.LEDGER_ROW, PostOfficeStyle.LEDGER_HOVER);
			graphics.text(font, font.plainSubstrByWidth(address, LEDGER_WIDTH - 16), x + 12, rowY + 1, PostOfficeStyle.INK, false);
		}
		if (addresses.size() > LEDGER_ROWS) {
			int track = PostOfficeStyle.LEDGER_HEIGHT - 4;
			int thumb = Math.max(6, track * LEDGER_ROWS / addresses.size());
			int thumbY = y + 2 + (track - thumb) * scroll / Math.max(1, addresses.size() - LEDGER_ROWS);
			graphics.fill(x + LEDGER_WIDTH - 4, thumbY, x + LEDGER_WIDTH - 2, thumbY + thumb, 0xAA8A6E45);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tab = menu.getTab();
		Component plaque = switch (tab) {
			case MailboxMenu.TAB_SEND -> Component.translatable("gui.postrider.mailbox.tab.send");
			case MailboxMenu.TAB_SETTINGS -> Component.translatable("gui.postrider.mailbox.tab.settings");
			default -> hasName() ? Component.literal(name) : Component.translatable("container.postrider.mailbox");
		};
		PostOfficeStyle.plaque(graphics, font, 0, 0, imageWidth, plaque);
		cutShort = null;

		if (tab == MailboxMenu.TAB_INBOX) {
			int count = inboxCount();
			Component status = count == 0 ? Component.translatable("gui.postrider.mailbox.inbox_empty")
				: count == 1 ? Component.translatable("gui.postrider.mailbox.inbox_one")
				: Component.translatable("gui.postrider.mailbox.inbox_many", count);
			PostOfficeStyle.write(graphics, font, status, MailboxMenu.INVENTORY_X, 80, PostOfficeStyle.INK);
		} else if (tab == MailboxMenu.TAB_SEND) {
			MailboxInfoCache.Notice notice = MailboxInfoCache.notice();
			if (selected != null && notice == null) {
				Component to = Component.translatable("gui.postrider.mailbox.to", font.plainSubstrByWidth(selected, LEDGER_WIDTH - 20));
				PostOfficeStyle.write(graphics, font, to, LEDGER_X, SEND_NOTICE_Y, PostOfficeStyle.INK);
			}
			if (notice != null)
				writeLines(graphics, notice.message(), LEDGER_X, SEND_NOTICE_Y, SEND_NOTICE_WIDTH, SEND_NOTICE_LINES,
					notice.good() ? PostOfficeStyle.INK : PostOfficeStyle.INK_ERROR);
		} else if (tab == MailboxMenu.TAB_SETTINGS) {
			PostOfficeStyle.write(graphics, font, Component.translatable("gui.postrider.mailbox.address"), 8, 20, PostOfficeStyle.INK);
			if (!ownerName.isEmpty())
				writeLines(graphics, Component.translatable("gui.postrider.mailbox.owner", ownerName), OWNER_X, 52, OWNER_WIDTH, 1, PostOfficeStyle.PENCIL);
			Component problem = nameProblem();
			MailboxInfoCache.Notice notice = MailboxInfoCache.notice();
			if (problem != null)
				writeLines(graphics, problem, 8, RULES_Y, RULES_WIDTH, STATUS_LINES, PostOfficeStyle.INK_ERROR);
			else if (notice != null)
				writeLines(graphics, notice.message(), 8, RULES_Y, RULES_WIDTH, STATUS_LINES, notice.good() ? PostOfficeStyle.INK : PostOfficeStyle.INK_ERROR);
			else
				writeLines(graphics, Component.translatable("gui.postrider.mailbox.name_rules", MailboxNames.MIN_LENGTH, MailboxNames.MAX_LENGTH),
					8, RULES_Y, RULES_WIDTH, 1, PostOfficeStyle.PENCIL);
		}
		PostOfficeStyle.write(graphics, font, playerInventoryTitle, MailboxMenu.INVENTORY_X, MailboxMenu.INVENTORY_LABEL_Y, PostOfficeStyle.CREAM);
	}

	/**
	 * A message written in at most this many lines of this width; if it doesn't fit, it's cut short with "..." and
	 * hovering over it shows the whole of it.
	 */
	private void writeLines(GuiGraphicsExtractor graphics, Component message, int x, int y, int width, int maxLines, int color) {
		String text = message.getString();
		List<String> lines = PostOfficeStyle.clamp(text, width, maxLines, font::width);
		for (int i = 0; i < lines.size(); i++)
			PostOfficeStyle.write(graphics, font, Component.literal(lines.get(i)), x, y + i * font.lineHeight, color);
		if (PostOfficeStyle.wrap(text, width, font::width).size() > maxLines) {
			cutShort = message;
			cutX = x;
			cutY = y;
			cutWidth = width;
			cutHeight = lines.size() * font.lineHeight;
		}
	}

	/** A tab's name over the tab, a message cut short in full over it, or else the item under the pointer. */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0)
			graphics.setTooltipForNextFrame(font, Component.translatable("gui.postrider.mailbox.tab." + TAB_KEYS[tab]), mouseX, mouseY);
		else if (cutShort != null && mouseX >= leftPos + cutX && mouseX < leftPos + cutX + cutWidth && mouseY >= topPos + cutY
				&& mouseY < topPos + cutY + cutHeight)
			graphics.setTooltipForNextFrame(font, font.split(cutShort, TOOLTIP_WIDTH), mouseX, mouseY);
		else
			super.extractTooltip(graphics, mouseX, mouseY);
	}

	// ---------------------------------------------------------------- input

	private int tabX(int tab) {
		return leftPos + 8 + tab * PostOfficeStyle.TAB_SPACING;
	}

	private int tabAt(double mouseX, double mouseY) {
		if (mouseY < topPos - PostOfficeStyle.TAB_HEIGHT || mouseY >= topPos)
			return -1;
		for (int k = 0; k < MailboxMenu.TAB_COUNT; k++)
			if (mouseX >= tabX(k) && mouseX < tabX(k) + PostOfficeStyle.TAB_WIDTH)
				return k;
		return -1;
	}

	private int ledgerRowAt(double mouseX, double mouseY) {
		int x = leftPos + LEDGER_X, y = topPos + LEDGER_Y + 1;
		if (mouseX < x || mouseX >= x + LEDGER_WIDTH || mouseY < y || mouseY >= y + LEDGER_ROWS * PostOfficeStyle.LEDGER_ROW)
			return -1;
		int row = (int) (mouseY - y) / PostOfficeStyle.LEDGER_ROW;
		return scroll + row < addresses.size() ? row : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0) {
			switchTab(tab);
			return true;
		}
		if (menu.getTab() == MailboxMenu.TAB_SEND && hasName()) {
			int row = ledgerRowAt(mouseX, mouseY);
			if (row >= 0) {
				selected = addresses.get(scroll + row);
				updateWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---------------------------------------------------------------- recordings

	/** The address picked in the ledger, or null; for Dailies to record (client/dailies). */
	@Nullable
	public String selectedAddress() {
		return selected;
	}

	/** How far the ledger is scrolled, in rows; for Dailies to record. */
	public int scrollRows() {
		return scroll;
	}

	/** What is in the address box, typed or not; for Dailies to record. */
	public String typedAddress() {
		return nameBox == null ? "" : nameBox.getValue();
	}

	/**
	 * Shows the screen as a recording has it, on a copy Dailies built to draw in playback: the tab, the address picked,
	 * the ledger's scroll and the address box. Called before every frame it is drawn, so it only changes what differs,
	 * and tells the server nothing.
	 */
	public void showRecorded(int tab, @Nullable String address, int scrollRows, String typed) {
		menu.setTab(tab);
		refreshInfo();
		selected = address;
		scroll = Mth.clamp(scrollRows, 0, Math.max(0, addresses.size() - LEDGER_ROWS));
		if (nameBox != null && !nameBox.getValue().equals(typed))
			nameBox.setValue(typed);
		updateWidgets();
	}

	/** Picks an address from the ledger, as clicking it would (for the screenshot run). */
	public void pickAddress(String address) {
		if (addresses.contains(address)) {
			selected = address;
			updateWidgets();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (menu.getTab() == MailboxMenu.TAB_SEND && addresses.size() > LEDGER_ROWS) {
			scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, addresses.size() - LEDGER_ROWS);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (nameBox.visible && nameBox.isFocused()) {
			int key = event.input();
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				save();
				return true;
			}
			if (key != InputConstants.KEY_ESCAPE)
				return nameBox.keyPressed(event) || nameBox.canConsumeInput();
		}
		return super.keyPressed(event);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		return super.hasClickedOutside(mouseX, mouseY, left, top) && tabAt(mouseX, mouseY) < 0;
	}
}
