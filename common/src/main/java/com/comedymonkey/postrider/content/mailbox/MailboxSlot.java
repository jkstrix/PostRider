package com.comedymonkey.postrider.content.mailbox;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot onto one of a mailbox's slots. Slots onto the same row share a container (an empty stand-in, one per row),
 * which is how the game tells which slots are the same inventory. It works on copies, as NeoForge's handler slot did when the mod used it:
 * the menu is handed a copy of the stack, and a change it makes to that copy is put back when the slot is told it
 * changed.
 */
public class MailboxSlot extends Slot {
	private final MailboxSlots slots;
	private final int index;
	@Nullable
	private ItemStack cachedReturnedStack = null;

	public MailboxSlot(MailboxSlots slots, int index, int x, int y) {
		super(slots.menuContainer(), index, x, y);
		this.slots = slots;
		this.index = index;
	}

	private ItemStack stackCopy() {
		return slots.getStackInSlot(index).copy();
	}

	@Override
	public final ItemStack getItem() {
		return cachedReturnedStack = stackCopy();
	}

	@Override
	public final void set(ItemStack stack) {
		slots.set(index, stack);
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
		return !slots.getStackInSlot(index).isEmpty();
	}
}
