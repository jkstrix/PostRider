package com.comedymonkey.postrider.post;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.comedymonkey.postrider.content.postman.Roster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps an eye on the riders out on the road, noting where each one is. One who has gone from the world without
 * finishing (removed by another mod or a command, say) is brought back after a minute, mail and all: where he was last
 * seen, or if that's lost (or nowhere to stand), at the sender's mailbox. If even the delivery is lost, he's simply
 * taken off the riders who are out. Should the old rider turn up after all, he sees he's been replaced and leaves.
 */
public final class RiderWatch {
	/** How long a rider can be missing (still loading after a restart, say) before he's brought back. */
	public static final int GRACE_TICKS = 60 * 20;

	/** Deliveries whose rider can't be found, and the game time he was first missed. */
	private static final Map<UUID, Long> MISSING_SINCE = new HashMap<>();

	private RiderWatch() {}

	public static boolean longGone(long missingSince, long now) {
		return now - missingSince >= GRACE_TICKS;
	}

	public static void check(MinecraftServer server) {
		PostOffice office = PostOffice.get(server);
		long now = server.overworld().getGameTime();
		for (PostOffice.Active each : office.active()) {
			UUID delivery = each.delivery().id();
			ServerLevel level = server.getLevel(each.delivery().from().dimension());
			if (level != null && level.getEntity(each.rider()) instanceof PostmanEntity rider && rider.isAlive()) {
				MISSING_SINCE.remove(delivery);
				office.saw(delivery, rider.blockPosition());
				continue;
			}
			long since = MISSING_SINCE.computeIfAbsent(delivery, id -> now);
			if (longGone(since, now))
				bringBack(office, level, each);
		}
		MISSING_SINCE.keySet().removeIf(id -> !office.isActive(id));
	}

	private static void bringBack(PostOffice office, ServerLevel level, PostOffice.Active gone) {
		UUID delivery = gone.delivery().id();
		if (level == null) {
			// The world his mail was in is gone: nothing to bring him back to
			office.finish(delivery);
			PostRider.LOGGER.warn("Rider {} vanished with mail for {} from a world that no longer exists; taking him off the road",
				gone.riderId(), gone.delivery().toName());
			return;
		}
		Roster.Rider who = Roster.byId(gone.riderId());
		if (who == null)
			who = Roster.pickFree(office.busyRiders(), level.getRandom());
		PostmanEntity back = who == null ? null : PostmanEntity.reappear(level, gone.delivery(), who, gone.lastSeen().orElse(null));
		// Nowhere to stand just now: try again at the next check
		if (back == null)
			return;
		MISSING_SINCE.remove(delivery);
		RiderChunks.forget(level, gone.rider());
		office.replaceRider(delivery, back.getUUID(), who.id());
		PostRider.LOGGER.warn("Rider {} vanished with mail for {}; brought back at {}", who.id(), gone.delivery().toName(), back.blockPosition());
	}
}
