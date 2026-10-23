package com.comedymonkey.postrider;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.route.TerrainSurvey;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.Posting;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * For development only: started with the system property {@code postrider.post} set to {@code "<from>|<to>"}
 * (the {@code runHeadless} Gradle task, on a copy of one of the client's worlds), the server sets the clock to
 * morning, posts a parcel from the one mailbox to the other, logs how the rider is getting on every quarter minute,
 * and stops once it's delivered or lost (or after half an hour), so a trip across a real world can be run and read
 * without anyone at the keyboard.
 */
public final class DevHeadless {
	public static final String PROPERTY = "postrider.post";
	/** Unless this is false, the server runs the trip as fast as it can (a tick sprint) rather than at twenty a second. */
	public static final String SPRINT_PROPERTY = "postrider.sprint";
	/** The server stops after this long whatever has happened (half an hour of game time, unless {@code postrider.timeout} says otherwise, in minutes). */
	public static final int TIMEOUT_TICKS = 20 * 60 * Integer.getInteger("postrider.timeout", 30);
	private static final int STATUS_INTERVAL = 20 * 15;
	private static final long MORNING = 1000;

	@Nullable
	private static PendingDelivery delivery;
	private static long startedAt;
	private static boolean done;

	private DevHeadless() {}

	/** The two names in the property, from and to; null if it isn't two names with a bar between. */
	@Nullable
	public static String[] parse(@Nullable String property) {
		if (property == null)
			return null;
		String[] names = property.split("\\|", -1);
		if (names.length != 2 || names[0].isBlank() || names[1].isBlank())
			return null;
		return new String[] {names[0].trim(), names[1].trim()};
	}

	/** Switches the run on if the server was started for it. */
	public static void registerIfWanted() {
		if (System.getProperty(PROPERTY) == null)
			return;
		PostRider.LOGGER.info("Post Rider headless run: the server will post {} and stop once it's delivered", System.getProperty(PROPERTY));
		NeoForge.EVENT_BUS.addListener(DevHeadless::onStarted);
		NeoForge.EVENT_BUS.addListener(DevHeadless::onTick);
	}

	private static void onStarted(ServerStartedEvent event) {
		MinecraftServer server = event.getServer();
		String[] names = parse(System.getProperty(PROPERTY));
		if (names == null) {
			PostRider.LOGGER.error("Post Rider headless run: {} should be \"<from box>|<to box>\", not {}", PROPERTY, System.getProperty(PROPERTY));
			server.halt(false);
			return;
		}
		ServerLevel overworld = server.overworld();
		overworld.setDayTime(MORNING);
		try {
			delivery = Posting.post(server, names[0], names[1]);
		} catch (IllegalArgumentException e) {
			PostRider.LOGGER.error("Post Rider headless run: {}", e.getMessage());
			server.halt(false);
			return;
		}
		// Nobody is about to keep the box's chunks loaded and ticking for the rider to set out from: the box's chunk and
		// the ring round it (he appears a few blocks from the box, maybe over a chunk's edge; and only a forced chunk
		// itself ticks its entities, not the ring it keeps loaded)
		ChunkPos box = new ChunkPos(delivery.from().pos());
		ServerLevel level = server.getLevel(delivery.from().dimension());
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				(level != null ? level : overworld).setChunkForced(box.x + dx, box.z + dz, true);
		startedAt = server.getTickCount();
		if (Boolean.parseBoolean(System.getProperty(SPRINT_PROPERTY, "true")))
			server.tickRateManager().requestGameToSprint(TIMEOUT_TICKS);
		PostRider.LOGGER.info("Post Rider headless run: posted from {} at {} to {}{}", delivery.fromName(), delivery.from().pos().toShortString(), delivery.toName(),
			server.tickRateManager().isSprinting() ? ", sprinting" : "");
	}

	private static void onTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (delivery == null || done)
			return;
		PostOffice office = PostOffice.get(server);
		boolean waiting = office.waiting().stream().anyMatch(d -> d.id().equals(delivery.id()));
		boolean active = office.isActive(delivery.id());
		long ticks = server.getTickCount() - startedAt;
		if (!waiting && !active) {
			done = true;
			server.tickRateManager().stopSprinting();
			PostRider.LOGGER.info("Post Rider headless run: finished after {} ticks ({} s): the parcel is delivered or lost (see the rider's lines above)", ticks, ticks / 20);
			server.halt(false);
			return;
		}
		if (ticks >= TIMEOUT_TICKS) {
			done = true;
			PostRider.LOGGER.warn("Post Rider headless run: giving up after {} ticks: {}", ticks, waiting ? "still waiting for a rider" : "the rider never got there");
			server.halt(false);
			return;
		}
		if (ticks % STATUS_INTERVAL == 0)
			for (PostOffice.Active out : office.active())
				if (out.delivery().id().equals(delivery.id()))
					for (ServerLevel level : server.getAllLevels())
						if (level.getEntity(out.rider()) instanceof PostmanEntity rider && rider.getRun() != null)
							PostRider.LOGGER.info("Post Rider headless run, {} s in: {} (at {}; survey: {})", ticks / 20,
								rider.getRun().describe(rider, level), rider.blockPosition().toShortString(), TerrainSurvey.of(level).describe());
	}
}
