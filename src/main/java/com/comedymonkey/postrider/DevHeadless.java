package com.comedymonkey.postrider;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.route.TerrainSurvey;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.Posting;
import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * For development only: started with the system property {@code postrider.post} set to {@code "<from>|<to>"}
 * (the {@code runHeadless} Gradle task, on a copy of one of the client's worlds), the server sets the clock to
 * morning, posts a parcel from the one mailbox to the other, logs how the rider is getting on every quarter minute,
 * and stops once it's delivered or lost (or after an hour of game time), so a trip across a real world can be run and read
 * without anyone at the keyboard. The box to post to can be one put down for the run: {@code random:<blocks>} puts a
 * box called "Far Post" on dry land about that far from the first box, any way round; {@code at:<x>,<z>} puts it
 * there (the ground height is found), so a spot can be tried again.
 */
public final class DevHeadless {
	public static final String PROPERTY = "postrider.post";
	/** Unless this is false, the server runs the trip as fast as it can (a tick sprint) rather than at twenty a second. */
	public static final String SPRINT_PROPERTY = "postrider.sprint";
	/** The server stops after this long whatever has happened (an hour of game time, unless {@code postrider.timeout} says otherwise, in minutes). */
	public static final int TIMEOUT_TICKS = 20 * 60 * Integer.getInteger("postrider.timeout", 60);
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

	/** Whether the server was started for the run (then its hooks are called; see PostRiderEvents). */
	public static boolean wanted() {
		if (System.getProperty(PROPERTY) == null)
			return false;
		PostRider.LOGGER.info("Post Rider headless run: the server will post {} and stop once it's delivered", System.getProperty(PROPERTY));
		return true;
	}

	public static void onStarted(MinecraftServer server) {
		String[] names = parse(System.getProperty(PROPERTY));
		if (names == null) {
			PostRider.LOGGER.error("Post Rider headless run: {} should be \"<from box>|<to box>\", not {}", PROPERTY, System.getProperty(PROPERTY));
			server.halt(false);
			return;
		}
		server.clockManager().setTotalTicks(server.registryAccess().getOrThrow(WorldClocks.OVERWORLD), MORNING);
		try {
			delivery = postNamed(server, names);
		} catch (IllegalArgumentException e) {
			PostRider.LOGGER.error("Post Rider headless run: {}", e.getMessage());
			server.halt(false);
			return;
		}
		startedAt = server.getTickCount();
		if (Boolean.parseBoolean(System.getProperty(SPRINT_PROPERTY, "true")))
			server.tickRateManager().requestGameToSprint(TIMEOUT_TICKS);
		PostRider.LOGGER.info("Post Rider headless run: posted from {} at {} to {}{}", delivery.fromName(), delivery.from().pos().toShortString(), delivery.toName(),
			server.tickRateManager().isSprinting() ? ", sprinting" : "");
	}

	/**
	 * Posts from one box to another as a run asks: {@code spawn} for the first puts a "Spawn Post" down by the world's
	 * spawn, {@code random:<blocks>} or {@code at:<x>,<z>} for the second puts a "Far Post" down there; and the first
	 * box's chunk and the ring round it are kept loaded and ticking for the rider to set out from (with nobody about,
	 * or a player elsewhere). Throws IllegalArgumentException with the reason if it can't.
	 */
	public static PendingDelivery postNamed(MinecraftServer server, String[] names) {
		if (names[0].equals("spawn"))
			names[0] = putDownSpawnBox(server);
		if (names[1].startsWith("random:") || names[1].startsWith("at:"))
			names[1] = putDownFarBox(server, names[0], names[1]);
		PendingDelivery posted = Posting.post(server, names[0], names[1]);
		ChunkPos box = ChunkPos.containing(posted.from().pos());
		ServerLevel level = server.getLevel(posted.from().dimension());
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				(level != null ? level : server.overworld()).setChunkForced(box.x() + dx, box.z() + dz, true);
		return posted;
	}

	public static final String FAR_BOX = "Far Post";
	public static final String SPAWN_BOX = "Spawn Post";
	/** Whose the boxes put down for a run are, with no player about. */
	public static final UUID TESTER = UUID.nameUUIDFromBytes("postrider headless tester".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	/** Puts a box down on dry land as near the world's spawn as can be found (a fresh world: {@code -Pfrom=spawn}). The box's name. */
	static String putDownSpawnBox(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getWorldBorderAdjustedRespawnData(level.getRespawnData()).pos();
		BlockPos spot = null;
		for (int radius = 0; radius <= 64 && spot == null; radius += 4)
			for (int dx = -radius; dx <= radius && spot == null; dx += 4)
				for (int dz = -radius; dz <= radius && spot == null; dz += 4)
					if (Math.max(Math.abs(dx), Math.abs(dz)) == radius)
						spot = dryLandAt(level, spawn.getX() + dx, spawn.getZ() + dz);
		if (spot == null)
			throw new IllegalArgumentException("No dry land near the spawn at " + spawn.toShortString());
		putDownBox(level, spot, SPAWN_BOX, TESTER);
		PostRider.LOGGER.info("Post Rider headless run: put the box {} down at {}", SPAWN_BOX, spot.toShortString());
		return SPAWN_BOX;
	}

	/** A mailbox (facing west) in the directory under this name and owner, at a spot on dry land. */
	static void putDownBox(ServerLevel level, BlockPos spot, String name, UUID owner) {
		BlockState lower = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState().setValue(MailboxBlock.FACING, Direction.WEST);
		level.setBlockAndUpdate(spot, lower);
		level.setBlockAndUpdate(spot.above(), lower.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER));
		if (!(level.getBlockEntity(spot) instanceof MailboxBlockEntity box))
			throw new IllegalArgumentException("No mailbox could be put down at " + spot.toShortString());
		MailboxDirectory.Result named = box.claim(name, owner);
		if (named != MailboxDirectory.Result.OK)
			throw new IllegalArgumentException("Couldn't name the box " + name + ": " + named);
	}
	/** Looking for dry land for the far box, the direction is turned this much at a time. */
	private static final double TURN_DEGREES = 10;

	/**
	 * Puts a mailbox down for the run: {@code random:<blocks>} about that far from the first box on dry land, any
	 * way round (turning until land is found), or {@code at:<x>,<z>} just there. The box's name.
	 */
	static String putDownFarBox(MinecraftServer server, String fromName, String where) {
		MailboxDirectory.Entry from = MailboxDirectory.get(server).find(fromName);
		if (from == null)
			throw new IllegalArgumentException("No mailbox called '" + fromName + "'");
		ServerLevel level = server.getLevel(from.pos().dimension());
		if (level == null)
			throw new IllegalArgumentException("The first box's world is gone");
		BlockPos spot;
		if (where.startsWith("at:")) {
			String[] xz = where.substring(3).split(",");
			spot = dryLandAt(level, Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim()));
			if (spot == null)
				throw new IllegalArgumentException("No dry land at " + where);
		} else {
			double distance = Double.parseDouble(where.substring("random:".length()));
			double angle = level.getRandom().nextDouble() * 360;
			spot = null;
			for (int turn = 0; turn < 360 / TURN_DEGREES && spot == null; turn++) {
				double radians = Math.toRadians(angle + turn * TURN_DEGREES);
				spot = dryLandAt(level, (int) Math.round(from.pos().pos().getX() + Math.cos(radians) * distance),
					(int) Math.round(from.pos().pos().getZ() + Math.sin(radians) * distance));
			}
			if (spot == null)
				throw new IllegalArgumentException("No dry land " + distance + " blocks from " + fromName + " any way round");
		}
		putDownBox(level, spot, FAR_BOX, from.owner());
		PostRider.LOGGER.info("Post Rider headless run: put the box {} down at {} (-Pto=at:{},{} tries this spot again)", FAR_BOX, spot.toShortString(), spot.getX(), spot.getZ());
		return FAR_BOX;
	}

	/** The block a box would stand in at (x, z): on the ground there if it's dry, solid and open to the sky; null if not. */
	@Nullable
	static BlockPos dryLandAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos ground = new BlockPos(x, y - 1, z), feet = new BlockPos(x, y, z);
		BlockState under = level.getBlockState(ground);
		if (!under.getFluidState().isEmpty() || !under.isFaceSturdy(level, ground, Direction.UP) || !level.getFluidState(feet).isEmpty()
				|| !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty())
			return null;
		return feet;
	}

	public static void onTick(MinecraftServer server) {
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
