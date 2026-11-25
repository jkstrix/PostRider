package com.comedymonkey.postrider.content.mailbox;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A fixed row of item slots in a mailbox (its inbox, or the parcel being packed). It behaves as NeoForge's stack
 * handler did when the mod used it (a slot holds up to its limit, and no more than the item stacks to), and saves the
 * same way, an "Items" list of stacks with their "Slot" and the "Size", so mailboxes in existing worlds keep their
 * mail.
 */
public class MailboxSlots {
	private NonNullList<ItemStack> stacks;
	private final int slotLimit;
	private final Runnable onChanged;

	/** Slots that hold full stacks and tell nobody when they change (the copy on a client's screen). */
	public MailboxSlots(int size) {
		this(size, Item.ABSOLUTE_MAX_STACK_SIZE, () -> {});
	}

	public MailboxSlots(int size, int slotLimit, Runnable onChanged) {
		this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
		this.slotLimit = slotLimit;
		this.onChanged = onChanged;
	}

	public int getSlots() {
		return stacks.size();
	}

	/** The stack in this slot itself, not a copy. */
	public ItemStack getStackInSlot(int slot) {
		validateSlotIndex(slot);
		return stacks.get(slot);
	}

	public void setStackInSlot(int slot, ItemStack stack) {
		validateSlotIndex(slot);
		stacks.set(slot, stack);
		onChanged.run();
	}

	public int getSlotLimit(int slot) {
		return slotLimit;
	}

	/**
	 * Takes up to this many from a slot (no more than the item stacks to), or with {@code simulate} only says what it
	 * would take.
	 */
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount == 0)
			return ItemStack.EMPTY;
		validateSlotIndex(slot);
		ItemStack existing = stacks.get(slot);
		if (existing.isEmpty())
			return ItemStack.EMPTY;
		int toExtract = Math.min(amount, existing.getMaxStackSize());
		if (existing.getCount() <= toExtract) {
			if (simulate)
				return existing.copy();
			stacks.set(slot, ItemStack.EMPTY);
			onChanged.run();
			return existing;
		}
		if (!simulate) {
			stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
			onChanged.run();
		}
		return existing.copyWithCount(toExtract);
	}

	public CompoundTag save(HolderLookup.Provider registries) {
		ListTag items = new ListTag();
		for (int i = 0; i < stacks.size(); i++) {
			if (!stacks.get(i).isEmpty()) {
				CompoundTag item = new CompoundTag();
				item.putInt("Slot", i);
				items.add(stacks.get(i).save(registries, item));
			}
		}
		CompoundTag tag = new CompoundTag();
		tag.put("Items", items);
		tag.putInt("Size", stacks.size());
		return tag;
	}

	public void load(HolderLookup.Provider registries, CompoundTag tag) {
		stacks = NonNullList.withSize(tag.contains("Size", Tag.TAG_INT) ? tag.getInt("Size") : stacks.size(), ItemStack.EMPTY);
		ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			CompoundTag item = items.getCompound(i);
			int slot = item.getInt("Slot");
			if (slot >= 0 && slot < stacks.size())
				ItemStack.parse(registries, item).ifPresent(stack -> stacks.set(slot, stack));
		}
	}

	private void validateSlotIndex(int slot) {
		if (slot < 0 || slot >= stacks.size())
			throw new RuntimeException("Slot " + slot + " not in valid range - [0," + stacks.size() + ")");
	}
}
