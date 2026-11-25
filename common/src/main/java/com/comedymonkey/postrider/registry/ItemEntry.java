package com.comedymonkey.postrider.registry;

import java.util.function.Function;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** An item the mod registers, usable wherever an item is wanted. */
public class ItemEntry<I extends Item> extends RegistryEntry<Item, I> implements ItemLike {
	ItemEntry(ResourceKey<Item> key, Identifier id, Function<Identifier, ? extends I> factory) {
		super(key, id, factory);
	}

	@Override
	public Item asItem() {
		return get();
	}

	public ItemStack toStack() {
		return toStack(1);
	}

	public ItemStack toStack(int count) {
		ItemStack stack = asItem().getDefaultInstance();
		if (stack.isEmpty())
			throw new IllegalStateException("Obtained empty item stack; incorrect getDefaultInstance() call?");
		stack.setCount(count);
		return stack;
	}
}
