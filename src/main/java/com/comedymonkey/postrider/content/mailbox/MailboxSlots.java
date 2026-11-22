package com.comedymonkey.postrider.content.mailbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A fixed row of item slots in a mailbox (its inbox, or the parcel being packed). It saves the way NeoForge's old
 * item handler did, an "Items" list of stacks with their "Slot" and the "Size", so mailboxes in existing worlds keep
 * their mail.
 */
public class MailboxSlots extends ItemStacksResourceHandler {
	private static final Codec<SavedStack> SAVED_STACK_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("Slot").forGetter(SavedStack::slot),
		ItemStack.MAP_CODEC.forGetter(SavedStack::stack)
	).apply(i, SavedStack::new));

	private final int slotLimit;
	private final Runnable onChanged;

	/** Slots that hold full stacks and tell nobody when they change (the copy on a client's screen). */
	public MailboxSlots(int size) {
		this(size, Item.ABSOLUTE_MAX_STACK_SIZE, () -> {});
	}

	public MailboxSlots(int size, int slotLimit, Runnable onChanged) {
		super(size);
		this.slotLimit = slotLimit;
		this.onChanged = onChanged;
	}

	public int getSlots() {
		return size();
	}

	/** The stack in this slot itself, not a copy. */
	public ItemStack getStackInSlot(int slot) {
		return stacks.get(slot);
	}

	public void setStackInSlot(int slot, ItemStack stack) {
		ItemStack previous = stacks.set(slot, stack);
		onContentsChanged(slot, previous);
	}

	@Override
	protected int getCapacity(int index, ItemResource resource) {
		return Math.min(slotLimit, super.getCapacity(index, resource));
	}

	@Override
	protected void onContentsChanged(int index, ItemStack previousContents) {
		onChanged.run();
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
