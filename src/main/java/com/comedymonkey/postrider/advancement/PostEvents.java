package com.comedymonkey.postrider.advancement;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.post.JourneyNotes;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModTriggers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The moments the post's advancements listen for, and firing them. */
public final class PostEvents {
	public static final String NAMED_MAILBOX = "named_mailbox";
	public static final String DYED_MAILBOX = "dyed_mailbox";
	public static final String SENT = "sent";
	public static final String SENT_LETTER = "sent_letter";
	public static final String RECEIVED = "received";
	/** Received mail that crossed water by barge or spent a night on the trail. */
	public static final String RECEIVED_WEATHERED = "received_weathered";
	public static final String GREETED = "greeted";
	public static final String FOUND_CAMP = "found_camp";
	public static final String RECOVERED = "recovered";
	public static final String RECOVERED_WRECK = "recovered_wreck";
	/** Opened the Lost Parcel of a rider you killed yourself. */
	public static final String RECOVERED_OWN_KILL = "recovered_own_kill";

	public static final List<String> ALL = List.of(NAMED_MAILBOX, DYED_MAILBOX, SENT, SENT_LETTER, RECEIVED, RECEIVED_WEATHERED,
		GREETED, FOUND_CAMP, RECOVERED, RECOVERED_WRECK, RECOVERED_OWN_KILL);

	private PostEvents() {}

	public static void fire(ServerPlayer player, String event) {
		fire(player, event, null, 1);
	}

	public static void fire(ServerPlayer player, String event, @Nullable String rider, int count) {
		ModTriggers.POST_EVENT.get().trigger(player, event, rider, count);
	}

	/** Mail for its owner (found in their mailbox, or arriving while they're about): received, and maybe weathered. */
	public static void received(ServerPlayer owner, Iterable<ItemStack> mail) {
		boolean any = false, weathered = false;
		for (ItemStack item : mail) {
			if (item.isEmpty())
				continue;
			any = true;
			weathered |= item.getOrDefault(ModDataComponents.JOURNEY_NOTES.get(), JourneyNotes.NONE).weathered();
		}
		if (any)
			fire(owner, RECEIVED);
		if (weathered)
			fire(owner, RECEIVED_WEATHERED);
	}
}
