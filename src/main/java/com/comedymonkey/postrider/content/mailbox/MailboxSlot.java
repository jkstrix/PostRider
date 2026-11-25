package com.comedymonkey.postrider.content.mailbox;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot onto one of a mailbox's slots. It works on copies, as NeoForge's handler slot did when the mod used it:
 * the menu is handed a copy of the stack, and a change it makes to that copy is put back when the slot is told it
 * changed.
 */
public class MailboxSlot extends Slot {
	private static final Container NO_CONTAINER = new SimpleContainer(0);

	private final MailboxSlots slots;
	@Nullable
	private ItemStack cachedReturnedStack = null;

	public MailboxSlot(MailboxSlots slots, int index, int x, int y) {
		super(NO_CONTAINER, index, x, y);
		this.slots = slots;
	}

	private ItemStack stackCopy() {
		return slots.getStackInSlot(getSlotIndex()).copy();
	}

	@Override
	public final ItemStack getItem() {
		return cachedReturnedStack = stackCopy();
	}

	@Override
	public final void set(ItemStack stack) {
		slots.set(getSlotIndex(), stack);
		cachedReturnedStack = stack;
	}

	@Override
	public final void setChanged() {
		if (cachedReturnedStack != null && !ItemStack.matches(cachedReturnedStack, stackCopy()))
			set(cachedReturnedStack);
	}

	@Override
	public ItemStack remove(int amount) {
		ItemStack stack = stackCopy();
		ItemStack taken = stack.split(amount);
		set(stack);
		cachedReturnedStack = null;
		return taken;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty();
	}

	@Override
	public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {}

	@Override
	public int getMaxStackSize() {
		return slots.capacity(ItemStack.EMPTY);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return slots.capacity(stack);
	}

	@Override
	public boolean mayPickup(Player player) {
		return !slots.getStackInSlot(getSlotIndex()).isEmpty();
	}

	@Override
	public boolean isSameInventory(Slot other) {
		return other instanceof MailboxSlot slot && slot.slots == this.slots;
	}
}
