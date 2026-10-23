package com.comedymonkey.postrider.post;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Mail posted from one mailbox to another by name, as a player would at the box (for testing, by command). */
public final class Posting {
	public static final String SENDER = "Tester";

	private Posting() {}

	/** Posts a parcel (a few loaves) from one box to another; throws if either isn't in the directory. */
	public static PendingDelivery post(MinecraftServer server, String fromName, String toName) {
		MailboxDirectory directory = MailboxDirectory.get(server);
		MailboxDirectory.Entry from = directory.find(fromName), to = directory.find(toName);
		if (from == null)
			throw new IllegalArgumentException("No mailbox called '" + fromName + "'");
		if (to == null)
			throw new IllegalArgumentException("No mailbox called '" + toName + "'");
		PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), from.owner(), SENDER, from.name(), from.pos(), to.name(),
			List.of(new ItemStack(Items.BREAD, 3)), server.overworld().getGameTime());
		PostOffice.get(server).enqueue(delivery);
		return delivery;
	}
}
