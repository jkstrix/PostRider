package com.comedymonkey.postrider.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class MailboxBlockEntityTest {
	private static MailboxBlockEntity mailbox() {
		return new MailboxBlockEntity(BlockPos.ZERO, ModBlocks.mailbox(DyeColor.WHITE).get().defaultBlockState());
	}

	@Test
	void startsUnnamedUnownedAndEmpty() {
		MailboxBlockEntity mailbox = mailbox();
		assertFalse(mailbox.hasName());
		assertNull(mailbox.getOwner());
		assertTrue(mailbox.isInboxEmpty());
		assertTrue(mailbox.isParcelEmpty());
	}

	@Test
	void inboxHoldsTwentySevenItemsOfMailThenRefuses() {
		MailboxBlockEntity mailbox = mailbox();
		for (int i = 0; i < MailboxBlockEntity.INBOX_SIZE; i++)
			assertTrue(mailbox.deliver(new ItemStack(Items.PAPER)), "delivery " + i);
		assertTrue(mailbox.isInboxFull());
		assertFalse(mailbox.deliver(new ItemStack(Items.PAPER)));
	}

	@Test
	void eachDeliveryTakesOnePlace() {
		MailboxBlockEntity mailbox = mailbox();
		mailbox.deliver(new ItemStack(Items.PAPER, 5));
		assertEquals(1, mailbox.getInbox().getStackInSlot(0).getCount());
		assertTrue(mailbox.getInbox().getStackInSlot(1).isEmpty());
	}

	@Test
	void sendingTakesEverythingOutOfTheParcelSlots() {
		MailboxBlockEntity mailbox = mailbox();
		mailbox.getParcel().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 12));
		mailbox.getParcel().setStackInSlot(4, new ItemStack(Items.BREAD, 3));
		List<ItemStack> taken = mailbox.takeParcelContents();
		assertEquals(2, taken.size());
		assertEquals(12, taken.get(0).getCount());
		assertEquals(Items.BREAD, taken.get(1).getItem());
		assertTrue(mailbox.isParcelEmpty());
	}

	@Test
	void keepsItsMailAcrossSaving() {
		var registries = VanillaRegistries.createLookup();
		MailboxBlockEntity mailbox = mailbox();
		mailbox.deliver(new ItemStack(Items.PAPER));
		mailbox.getParcel().setStackInSlot(2, new ItemStack(Items.EMERALD, 7));
		CompoundTag saved = mailbox.saveWithoutMetadata(registries);

		MailboxBlockEntity loaded = mailbox();
		loaded.loadCustomOnly(saved, registries);
		assertFalse(loaded.isInboxEmpty());
		assertEquals(7, loaded.getParcel().getStackInSlot(2).getCount());
	}

	@Test
	void clientUpdatesCarryOnlyTheAddressAndOwner() {
		var registries = VanillaRegistries.createLookup();
		MailboxBlockEntity mailbox = mailbox();
		mailbox.deliver(new ItemStack(Items.PAPER));
		CompoundTag update = mailbox.getUpdateTag(registries);
		assertFalse(update.contains("inbox"));
		assertFalse(update.contains("parcel"));
		assertTrue(update.contains("name"));
	}
}
