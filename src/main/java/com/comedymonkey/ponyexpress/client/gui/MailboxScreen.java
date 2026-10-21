package com.comedymonkey.ponyexpress.client.gui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.content.mailbox.MailboxInfoCache;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxMenu;
import com.comedymonkey.ponyexpress.network.RenameMailboxPayload;
import com.comedymonkey.ponyexpress.network.SendParcelPayload;
import com.comedymonkey.ponyexpress.post.MailboxNames;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/** The mailbox's three tabs: Inbox, Send and Settings, dressed as an old post office counter. */
public class MailboxScreen extends AbstractContainerScreen<MailboxMenu> {
	private static final int[] TAB_ICONS = {PostOfficeStyle.ICON_INBOX, PostOfficeStyle.ICON_SEND, PostOfficeStyle.ICON_SETTINGS};
	private static final String[] TAB_KEYS = {"inbox", "send", "settings"};
	private static final int LEDGER_X = 68;
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

	public MailboxScreen(MailboxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = MailboxMenu.WIDTH;
		imageHeight = MailboxMenu.HEIGHT;
		name = title.getString();
	}

	@Override
	protected void init() {
		super.init();
		nameBox = new InkTextField(font, leftPos + 11, topPos + 32, 150, 9, Component.translatable("gui.ponyexpress.mailbox.address"));
		nameBox.setMaxLength(MailboxNames.MAX_LENGTH);
		nameBox.setValue(hasName() ? name : "");
		addRenderableWidget(nameBox);
		saveButton = addRenderableWidget(new PostOfficeButton(leftPos + 8, topPos + 48,
			Component.translatable("gui.ponyexpress.mailbox.save"), PostOfficeStyle.ButtonStyle.BRASS, this::save));
		sendButton = addRenderableWidget(new PostOfficeButton(leftPos + 7, topPos + 75,
			Component.translatable("gui.ponyexpress.mailbox.send"), PostOfficeStyle.ButtonStyle.WAX, this::send));
		refreshInfo();
		updateWidgets();
	}

	private boolean hasName() {
		return !name.isEmpty() && !name.equals(Component.translatable("container.ponyexpress.mailbox").getString());
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

	private void switchTab(int tab) {
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
		PacketDistributor.sendToServer(new RenameMailboxPayload(nameBox.getValue()));
		nameBox.setFocused(false);
	}

	private void send() {
		if (selected != null)
			PacketDistributor.sendToServer(new SendParcelPayload(selected));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshInfo();
		updateWidgets();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		for (int k = 0; k < MailboxMenu.TAB_COUNT; k++)
			if (k != menu.getTab())
				PostOfficeStyle.tab(graphics, tabX(k), topPos, k, TAB_ICONS[k], false);
		PostOfficeStyle.panel(graphics, leftPos, topPos);
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
			PostOfficeStyle.textField(graphics, leftPos + 8, topPos + 30);
	}

	private void renderLedger(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = leftPos + LEDGER_X, y = topPos + LEDGER_Y;
		PostOfficeStyle.ledger(graphics, x, y);
		if (!hasName()) {
			graphics.drawWordWrap(font, Component.translatable("gui.ponyexpress.mailbox.name_first"), x + 12, y + 3, 84, PostOfficeStyle.INK_ERROR);
			return;
		}
		if (addresses.isEmpty()) {
			graphics.drawWordWrap(font, Component.translatable("gui.ponyexpress.mailbox.no_addresses"), x + 12, y + 3, 84, PostOfficeStyle.PENCIL);
			return;
		}
		int hovered = ledgerRowAt(mouseX, mouseY);
		for (int row = 0; row < LEDGER_ROWS && scroll + row < addresses.size(); row++) {
			String address = addresses.get(scroll + row);
			int rowY = y + 1 + row * PostOfficeStyle.LEDGER_ROW;
			if (address.equals(selected))
				graphics.fill(x + 1, rowY, x + PostOfficeStyle.LEDGER_WIDTH - 1, rowY + PostOfficeStyle.LEDGER_ROW, PostOfficeStyle.LEDGER_SELECTED);
			else if (row == hovered)
				graphics.fill(x + 1, rowY, x + PostOfficeStyle.LEDGER_WIDTH - 1, rowY + PostOfficeStyle.LEDGER_ROW, PostOfficeStyle.LEDGER_HOVER);
			graphics.drawString(font, font.plainSubstrByWidth(address, 84), x + 12, rowY + 1, PostOfficeStyle.INK, false);
		}
		if (addresses.size() > LEDGER_ROWS) {
			int track = PostOfficeStyle.LEDGER_HEIGHT - 4;
			int thumb = Math.max(6, track * LEDGER_ROWS / addresses.size());
			int thumbY = y + 2 + (track - thumb) * scroll / Math.max(1, addresses.size() - LEDGER_ROWS);
			graphics.fill(x + PostOfficeStyle.LEDGER_WIDTH - 4, thumbY, x + PostOfficeStyle.LEDGER_WIDTH - 2, thumbY + thumb, 0xAA8A6E45);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		int tab = menu.getTab();
		Component plaque = switch (tab) {
			case MailboxMenu.TAB_SEND -> Component.translatable("gui.ponyexpress.mailbox.tab.send");
			case MailboxMenu.TAB_SETTINGS -> Component.translatable("gui.ponyexpress.mailbox.tab.settings");
			default -> hasName() ? Component.literal(name) : Component.translatable("container.ponyexpress.mailbox");
		};
		PostOfficeStyle.plaque(graphics, font, 0, 0, plaque);

		if (tab == MailboxMenu.TAB_INBOX) {
			int count = inboxCount();
			Component status = count == 0 ? Component.translatable("gui.ponyexpress.mailbox.inbox_empty")
				: count == 1 ? Component.translatable("gui.ponyexpress.mailbox.inbox_one")
				: Component.translatable("gui.ponyexpress.mailbox.inbox_many", count);
			PostOfficeStyle.write(graphics, font, status, 8, 80, PostOfficeStyle.INK);
		} else if (tab == MailboxMenu.TAB_SEND && selected != null) {
			Component to = Component.translatable("gui.ponyexpress.mailbox.to", font.plainSubstrByWidth(selected, 70));
			PostOfficeStyle.write(graphics, font, to, LEDGER_X, 68, PostOfficeStyle.INK);
		} else if (tab == MailboxMenu.TAB_SETTINGS) {
			PostOfficeStyle.write(graphics, font, Component.translatable("gui.ponyexpress.mailbox.address"), 8, 20, PostOfficeStyle.INK);
			if (!ownerName.isEmpty())
				PostOfficeStyle.write(graphics, font, Component.translatable("gui.ponyexpress.mailbox.owner", ownerName), 70, 52, PostOfficeStyle.PENCIL);
			graphics.drawWordWrap(font, Component.translatable("gui.ponyexpress.mailbox.name_rules", MailboxNames.MIN_LENGTH, MailboxNames.MAX_LENGTH),
				8, 70, 160, PostOfficeStyle.PENCIL);
		}
		PostOfficeStyle.write(graphics, font, playerInventoryTitle, 8, MailboxMenu.INVENTORY_LABEL_Y, PostOfficeStyle.CREAM);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0)
			graphics.renderTooltip(font, Component.translatable("gui.ponyexpress.mailbox.tab." + TAB_KEYS[tab]), mouseX, mouseY);
		else
			renderTooltip(graphics, mouseX, mouseY);
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
		if (mouseX < x || mouseX >= x + PostOfficeStyle.LEDGER_WIDTH || mouseY < y || mouseY >= y + LEDGER_ROWS * PostOfficeStyle.LEDGER_ROW)
			return -1;
		int row = (int) (mouseY - y) / PostOfficeStyle.LEDGER_ROW;
		return scroll + row < addresses.size() ? row : -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
		return super.mouseClicked(mouseX, mouseY, button);
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
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (nameBox.visible && nameBox.isFocused()) {
			if (keyCode == 257 || keyCode == 335) {
				save();
				return true;
			}
			if (keyCode != 256)
				return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput();
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		return super.hasClickedOutside(mouseX, mouseY, left, top, button) && tabAt(mouseX, mouseY) < 0;
	}
}
