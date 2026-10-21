package com.comedymonkey.postrider.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class MailboxMenuTest {
	private final MailboxBlockEntity mailbox =
		new MailboxBlockEntity(BlockPos.ZERO, ModBlocks.mailbox(DyeColor.WHITE).get().defaultBlockState());
	private final Inventory inventory = new Inventory(null);
	private final MailboxMenu menu = new MailboxMenu(1, inventory, mailbox);

	@Test
	void hasInboxParcelAndInventorySlots() {
		assertEquals(27 + 9 + 36, menu.slots.size());
		assertEquals(MailboxMenu.PLAYER_END, menu.slots.size());
	}

	@Test
	void onlyTheOpenTabsSlotsAreActive() {
		assertTrue(menu.getSlot(MailboxMenu.INBOX_START).isActive());
		assertFalse(menu.getSlot(MailboxMenu.PARCEL_START).isActive());
		menu.clickMenuButton(null, MailboxMenu.TAB_SEND);
		assertFalse(menu.getSlot(MailboxMenu.INBOX_START).isActive());
		assertTrue(menu.getSlot(MailboxMenu.PARCEL_START).isActive());
		menu.clickMenuButton(null, MailboxMenu.TAB_SETTINGS);
		assertFalse(menu.getSlot(MailboxMenu.PARCEL_START).isActive());
		assertTrue(menu.getSlot(MailboxMenu.PLAYER_START).isActive());
	}

	@Test
	void ignoresUnknownTabs() {
		assertFalse(menu.clickMenuButton(null, 7));
		assertEquals(MailboxMenu.TAB_INBOX, menu.getTab());
	}

	@Test
	void nothingCanBePutIntoTheInbox() {
		assertFalse(menu.getSlot(MailboxMenu.INBOX_START).mayPlace(new ItemStack(Items.DIAMOND)));
	}

	@Test
	void shiftClickingFromTheInventoryFillsTheParcelOnlyOnTheSendTab() {
		inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		int hotbarSlot = MailboxMenu.PLAYER_START + 27;

		assertTrue(menu.quickMoveStack(null, hotbarSlot).isEmpty());
		assertTrue(mailbox.isParcelEmpty());

		menu.clickMenuButton(null, MailboxMenu.TAB_SEND);
		menu.quickMoveStack(null, hotbarSlot);
		assertEquals(10, mailbox.getParcel().getStackInSlot(0).getCount());
		assertTrue(inventory.getItem(0).isEmpty());
	}

	@Test
	void shiftClickingTakesMailOutOfTheInbox() {
		mailbox.deliver(new ItemStack(Items.PAPER));
		menu.quickMoveStack(null, MailboxMenu.INBOX_START);
		assertTrue(mailbox.isInboxEmpty());
		boolean found = false;
		for (int i = 0; i < inventory.getContainerSize(); i++)
			found |= inventory.getItem(i).is(Items.PAPER);
		assertTrue(found);
	}
}
