package com.comedymonkey.postrider.content.mailbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A fixed row of item slots in a mailbox (its inbox, or the parcel being packed). It saves the way NeoForge's old
 * item handler did, an "Items" list of stacks with their "Slot" and the "Size", so mailboxes in existing worlds keep
 * their mail. (It behaves as NeoForge's stack handler did when the mod used it: a slot holds up to its limit, and no
 * more than the item stacks to.)
 */
public class MailboxSlots {
	private static final Codec<SavedStack> SAVED_STACK_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("Slot").forGetter(SavedStack::slot),
		ItemStack.MAP_CODEC.forGetter(SavedStack::stack)
	).apply(i, SavedStack::new));

	private final NonNullList<ItemStack> stacks;
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
		return stacks.get(slot);
	}

	public void setStackInSlot(int slot, ItemStack stack) {
		stacks.set(slot, stack);
		onChanged.run();
	}

	/** Puts a copy of a stack in a slot (an empty stack empties it). */
	public void set(int slot, ItemStack stack) {
		setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
	}

	/** How many of an item a slot can hold: its limit, and no more than the item stacks to (an empty stack: the limit). */
	public int capacity(ItemStack stack) {
		int itemLimit = stack.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : Math.min(stack.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
		return Math.min(slotLimit, itemLimit);
	}

	public void save(ValueOutput output) {
		ValueOutput.TypedOutputList<SavedStack> items = output.list("Items", SAVED_STACK_CODEC);
		for (int i = 0; i < stacks.size(); i++)
			if (!stacks.get(i).isEmpty())
				items.add(new SavedStack(i, stacks.get(i)));
		output.putInt("Size", stacks.size());
	}

	public void load(ValueInput input) {
		for (int i = 0; i < stacks.size(); i++)
			stacks.set(i, ItemStack.EMPTY);
		for (SavedStack saved : input.listOrEmpty("Items", SAVED_STACK_CODEC))
			if (saved.slot() >= 0 && saved.slot() < stacks.size())
				stacks.set(saved.slot(), saved.stack());
	}

	private record SavedStack(int slot, ItemStack stack) {}
}
