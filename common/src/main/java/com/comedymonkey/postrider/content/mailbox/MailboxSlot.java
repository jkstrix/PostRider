package com.comedymonkey.postrider.content.mailbox;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot onto one of a mailbox's slots, behaving as NeoForge's handler slot did when the mod used it. Like that
 * slot, every mailbox slot stands on the same empty container, so the game counts them all as one inventory (when a
 * double click gathers items, say).
 */
public class MailboxSlot extends Slot {
	private static final Container NO_CONTAINER = new SimpleContainer(0);

	private final MailboxSlots slots;
	private final int slotIndex;

	public MailboxSlot(MailboxSlots slots, int index, int x, int y) {
		super(NO_CONTAINER, index, x, y);
		this.slots = slots;
		this.slotIndex = index;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty();
	}

	@Override
	public ItemStack getItem() {
		return slots.getStackInSlot(slotIndex);
	}

	@Override
	public void set(ItemStack stack) {
		slots.setStackInSlot(slotIndex, stack);
		setChanged();
	}

	@Override
	public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {}

	@Override
	public int getMaxStackSize() {
		return slots.getSlotLimit(slotIndex);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return Math.min(stack.getMaxStackSize(), slots.getSlotLimit(slotIndex));
	}

	@Override
	public boolean mayPickup(Player player) {
		return !slots.extractItem(slotIndex, 1, true).isEmpty();
	}

	@Override
	public ItemStack remove(int amount) {
		return slots.extractItem(slotIndex, amount, false);
	}
}
