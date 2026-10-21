package com.comedymonkey.ponyexpress.post;

import java.util.ArrayList;
import java.util.List;

import com.comedymonkey.ponyexpress.content.letter.LetterContent;
import com.comedymonkey.ponyexpress.content.parcel.ParcelLabel;
import com.comedymonkey.ponyexpress.registry.ModDataComponents;
import com.comedymonkey.ponyexpress.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Turns what was put in a mailbox's parcel slots into the one item of mail that arrives. */
public final class Mail {
	private Mail() {}

	/** True if the parcel slots held nothing but a single written letter, which then travels on its own. */
	public static boolean isLetterOnly(List<ItemStack> items) {
		return items.size() == 1 && items.get(0).is(ModItems.WRITTEN_LETTER.get()) && items.get(0).getCount() == 1;
	}

	/** The in-game day number used on postmarks, counting from day 1. */
	public static long day(long dayTime) {
		return dayTime / 24000L + 1;
	}

	/**
	 * The mail for a delivery: the letter itself if it was sent alone, otherwise a parcel with everything inside.
	 * Every written letter gets the sending mailbox's postmark.
	 */
	public static ItemStack wrap(PendingDelivery delivery, long day) {
		LetterContent.Postmark postmark = new LetterContent.Postmark(delivery.fromName(), day);
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack item : delivery.items())
			items.add(postmarked(item, postmark));
		if (isLetterOnly(items))
			return items.get(0);
		ItemStack parcel = new ItemStack(ModItems.PARCEL.get());
		parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		parcel.set(ModDataComponents.PARCEL_LABEL.get(), new ParcelLabel(delivery.fromName(), delivery.toName(), delivery.senderName(), day));
		return parcel;
	}

	private static ItemStack postmarked(ItemStack item, LetterContent.Postmark postmark) {
		ItemStack copy = item.copy();
		LetterContent letter = copy.get(ModDataComponents.LETTER.get());
		if (letter != null && letter.postmark().isEmpty())
			copy.set(ModDataComponents.LETTER.get(), letter.postmarked(postmark));
		return copy;
	}
}
