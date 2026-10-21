package com.comedymonkey.ponyexpress.registry;

import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.ponyexpress.PonyExpress;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PonyExpress.MOD_ID);

	private static final Map<DyeColor, DeferredItem<DoubleHighBlockItem>> MAILBOXES = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			MAILBOXES.put(color, ITEMS.register(color.getSerializedName() + "_mailbox",
				() -> new DoubleHighBlockItem(ModBlocks.mailbox(color).get(), new Item.Properties())));
		}
	}

	private ModItems() {}

	public static DeferredItem<DoubleHighBlockItem> mailbox(DyeColor color) {
		return MAILBOXES.get(color);
	}
}
