package com.comedymonkey.postrider.registry;

import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.hopper.IceHopperItem;
import com.comedymonkey.postrider.content.letter.BlankLetterItem;
import com.comedymonkey.postrider.content.letter.WrittenLetterItem;
import com.comedymonkey.postrider.content.parcel.ParcelItem;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;

public final class ModItems {
	public static final Registrar<Item> ITEMS = Registrar.create(Registries.ITEM);

	private static final Map<DyeColor, ItemEntry<DoubleHighBlockItem>> MAILBOXES = new EnumMap<>(DyeColor.class);

	public static final ItemEntry<BlankLetterItem> LETTER =
		ITEMS.registerItem("letter", BlankLetterItem::new, properties -> properties.stacksTo(16));
	public static final ItemEntry<WrittenLetterItem> WRITTEN_LETTER =
		ITEMS.registerItem("written_letter", WrittenLetterItem::new, properties -> properties.stacksTo(1));
	public static final ItemEntry<ParcelItem> PARCEL =
		ITEMS.registerItem("parcel", ParcelItem::new, properties -> properties.stacksTo(1));
	public static final ItemEntry<IceHopperItem> ICE_HOPPER =
		ITEMS.registerItem("ice_hopper", IceHopperItem::new, properties -> properties.stacksTo(1));

	static {
		for (DyeColor color : DyeColor.values()) {
			MAILBOXES.put(color, ITEMS.registerItem(color.getSerializedName() + "_mailbox",
				properties -> new DoubleHighBlockItem(ModBlocks.mailbox(color).get(), properties), Item.Properties::useBlockDescriptionPrefix));
		}
	}

	private ModItems() {}

	public static ItemEntry<DoubleHighBlockItem> mailbox(DyeColor color) {
		return MAILBOXES.get(color);
	}
}
