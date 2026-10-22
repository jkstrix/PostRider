package com.comedymonkey.postrider.content.mailbox;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The mailbox screen's slots: the 27-parcel inbox (take only), the 9 parcel slots and the player's inventory.
 * Only the open tab's slots are active; the screen tells the server which tab that is through
 * {@link #clickMenuButton}.
 */
public class MailboxMenu extends AbstractContainerMenu {
	public static final int TAB_INBOX = 0;
	public static final int TAB_SEND = 1;
	public static final int TAB_SETTINGS = 2;
	public static final int TAB_COUNT = 3;

	/** Wider than a chest, so addresses and messages have room. */
	public static final int WIDTH = 248;
	public static final int HEIGHT = 192;
	public static final int GRID_X = 8;
	/** The inbox and the player's inventory, nine slots across, sit in the middle. */
	public static final int INVENTORY_X = (WIDTH - 9 * 18) / 2 + 1;
	public static final int GRID_Y = 20;
	public static final int INVENTORY_LABEL_Y = 98;
	public static final int INVENTORY_Y = 110;
	public static final int HOTBAR_Y = 168;

	public static final int INBOX_START = 0;
	public static final int PARCEL_START = INBOX_START + MailboxBlockEntity.INBOX_SIZE;
	public static final int PLAYER_START = PARCEL_START + MailboxBlockEntity.PARCEL_SIZE;
	public static final int PLAYER_END = PLAYER_START + 36;

	private final BlockPos pos;
	@Nullable
	private final MailboxBlockEntity mailbox;
	private int tab = TAB_INBOX;
	/** The address it had when opened ("" if none yet); the screen keeps up with changes from there. */
	private final String openedName;

	/** Server side, on the real mailbox. */
	public MailboxMenu(int containerId, Inventory inventory, MailboxBlockEntity mailbox) {
		this(containerId, inventory, mailbox.getBlockPos(), mailbox, mailbox.getInbox(), mailbox.getParcel(), mailbox.getName());
	}

	/** Client side, from the position the server sent. */
	public MailboxMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
		this(containerId, inventory, buf.readBlockPos(), null,
			new ItemStackHandler(MailboxBlockEntity.INBOX_SIZE), new ItemStackHandler(MailboxBlockEntity.PARCEL_SIZE), buf.readUtf());
	}

	private MailboxMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable MailboxBlockEntity mailbox,
			IItemHandler inbox, IItemHandler parcel, String openedName) {
		super(ModMenus.MAILBOX.get(), containerId);
		this.pos = pos;
		this.mailbox = mailbox;
		this.openedName = openedName;

		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				addSlot(new TabSlot(inbox, row * 9 + col, INVENTORY_X + col * 18, GRID_Y + row * 18, TAB_INBOX, false));
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 3; col++)
				addSlot(new TabSlot(parcel, row * 3 + col, GRID_X + col * 18, GRID_Y + row * 18, TAB_SEND, true));
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
		for (int col = 0; col < 9; col++)
			addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
	}

	public String getOpenedName() {
		return openedName;
	}

	public BlockPos getPos() {
		return pos;
	}

	@Nullable
	public MailboxBlockEntity getMailbox() {
		return mailbox;
	}

	public int getTab() {
		return tab;
	}

	/** Client side: switch tabs locally; the screen also sends the button click so the server follows. */
	public void setTab(int tab) {
		if (tab >= 0 && tab < TAB_COUNT)
			this.tab = tab;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id < 0 || id >= TAB_COUNT)
			return false;
		tab = id;
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.isActive())
			return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < PLAYER_START) {
			if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true))
				return ItemStack.EMPTY;
		} else if (tab == TAB_SEND) {
			if (!moveItemStackTo(stack, PARCEL_START, PLAYER_START, false))
				return ItemStack.EMPTY;
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty())
			slot.setByPlayer(ItemStack.EMPTY);
		else
			slot.setChanged();
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		if (mailbox == null)
			return true;
		return !mailbox.isRemoved() && mailbox.isOwner(player) && player.canInteractWithBlock(pos, 4.0);
	}

	/** A slot that only takes part while its tab is open. */
	private class TabSlot extends SlotItemHandler {
		private final int slotTab;
		private final boolean placeable;

		TabSlot(IItemHandler handler, int index, int x, int y, int slotTab, boolean placeable) {
			super(handler, index, x, y);
			this.slotTab = slotTab;
			this.placeable = placeable;
		}

		@Override
		public boolean isActive() {
			return tab == slotTab;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return placeable && isActive() && super.mayPlace(stack);
		}

		@Override
		public boolean mayPickup(Player player) {
			return isActive() && super.mayPickup(player);
		}
	}
}
