package com.comedymonkey.postrider.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.parcel.ParcelLabel;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModItems;

import net.minecraft.core.BlockPos;
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
		return wrap(delivery, day, 0);
	}

	/** As {@link #wrap(PendingDelivery, long)}, noting on the label and postmarks how many game ticks it took. */
	public static ItemStack wrap(PendingDelivery delivery, long day, long travelTicks) {
		LetterContent.Postmark postmark = new LetterContent.Postmark(delivery.fromName(), day, travelTicks);
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack item : delivery.items())
			items.add(postmarked(item, postmark));
		if (isLetterOnly(items))
			return items.get(0);
		ItemStack parcel = new ItemStack(ModItems.PARCEL.get());
		parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		parcel.set(ModDataComponents.PARCEL_LABEL.get(), new ParcelLabel(delivery.fromName(), delivery.toName(), delivery.senderName(), day, travelTicks));
		return parcel;
	}

	public static final String COMPANY = "Post Rider Mail Co.";

	/**
	 * The letter the post office sends when mail goes astray: what happened, and where the Lost Parcel lies (or, if
	 * there was nowhere to leave one, where its contents fell).
	 */
	public static ItemStack failureLetter(PendingDelivery delivery, String reason, BlockPos at, boolean parcelLeft, long day) {
		String what = isLetterOnly(delivery.items()) ? "letter" : "parcel";
		String where = "X " + at.getX() + ", Y " + at.getY() + ", Z " + at.getZ();
		// Where to find it goes on the first page, so long names or a long account can never push it off the page
		String first = "Dear " + delivery.senderName() + ",\n\nWe regret that your " + what + " to " + delivery.toName()
			+ " did not arrive.\n\n" + (parcelLeft
				? "It lies at " + where + ", marked LOST."
				: "Its contents lie scattered near " + where + ".");
		String second = reason;
		String third = (parcelLeft
			? "Only you, the addressee or whoever bested the rider may open it."
			: "There was nowhere to leave it. Ride out soon, before somebody else finds them.") + "\n\nWith apologies,\nthe " + COMPANY;
		ItemStack letter = new ItemStack(ModItems.WRITTEN_LETTER.get());
		letter.set(ModDataComponents.LETTER.get(), new LetterContent(List.of(first, second, third), COMPANY,
			Optional.of(new LetterContent.Postmark(COMPANY, day))));
		return letter;
	}

	private static ItemStack postmarked(ItemStack item, LetterContent.Postmark postmark) {
		ItemStack copy = item.copy();
		LetterContent letter = copy.get(ModDataComponents.LETTER.get());
		if (letter != null && letter.postmark().isEmpty())
			copy.set(ModDataComponents.LETTER.get(), letter.postmarked(postmark));
		return copy;
	}
}
