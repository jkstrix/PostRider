package com.comedymonkey.ponyexpress.registry;

import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.content.letter.BlankLetterItem;
import com.comedymonkey.ponyexpress.content.letter.WrittenLetterItem;
import com.comedymonkey.ponyexpress.content.parcel.ParcelItem;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PonyExpress.MOD_ID);

	private static final Map<DyeColor, DeferredItem<DoubleHighBlockItem>> MAILBOXES = new EnumMap<>(DyeColor.class);

	public static final DeferredItem<BlankLetterItem> LETTER =
		ITEMS.register("letter", () -> new BlankLetterItem(new Item.Properties().stacksTo(16)));
	public static final DeferredItem<WrittenLetterItem> WRITTEN_LETTER =
		ITEMS.register("written_letter", () -> new WrittenLetterItem(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<ParcelItem> PARCEL =
		ITEMS.register("parcel", () -> new ParcelItem(new Item.Properties().stacksTo(1)));

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
