package com.comedymonkey.postrider.post;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sends a rider out for each waiting parcel during the day, up to {@link #MAX_RIDERS} on the road at once: the whole
 * crew, each out on one delivery at a time.
 */
public final class PostDispatcher {
	public static final int MAX_RIDERS = Roster.ALL.size();
	/** How often the post office looks for mail to send out. */
	public static final int INTERVAL = 40;

	private PostDispatcher() {}

	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server.getTickCount() % INTERVAL != 0)
			return;
		RiderWatch.check(server);
		if (PostOfficeHours.isOpen(server.overworld().getDayTime()))
			dispatch(server);
	}

	/** Whether there's a rider free and mail waiting. */
	public static boolean canSendMore(int riding, int waiting) {
		return riding < MAX_RIDERS && waiting > 0;
	}

	/** Sends out as many riders as there's mail and room for; returns how many set out. */
	public static int dispatch(MinecraftServer server) {
		PostOffice office = PostOffice.get(server);
		int sent = 0;
		while (canSendMore(office.activeCount(), office.size())) {
			Roster.Rider who = Roster.pickFree(office.busyRiders(), server.overworld().getRandom());
			if (who == null)
				break;
			PendingDelivery next = office.poll();
			ServerLevel level = server.getLevel(next.from().dimension());
			PostmanEntity rider = level == null ? null : PostmanEntity.setOut(level, next, who);
			if (rider == null) {
				PostRider.LOGGER.warn("No rider could set out from {} for {}; trying again later", next.fromName(), next.toName());
				office.putBack(next);
				break;
			}
			office.setOut(next, rider.getUUID(), who.id());
			sent++;
		}
		return sent;
	}
}
