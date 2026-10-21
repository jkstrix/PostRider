package com.comedymonkey.postrider.content.postman;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.post.PostOffice;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps the chunks around a working rider loaded, so he carries on riding when nobody is near him: a square ticking
 * close round him, and a wider one loaded (but not ticking, so nothing grows or spawns there) for planning his route a
 * stretch ahead. The tickets are saved with the world; when it loads, any left over from riders who are no longer
 * working are dropped.
 */
public final class RiderChunks {
	/** Chunks loaded in each direction around him: 3 makes a 7x7 square, reaching past a 48-block stretch. */
	public static final int RADIUS = 3;
	/** Of those, the ones that tick (mobs, crops, his own goings-on): 1 makes the 3x3 square he's in the middle of. */
	public static final int TICKING_RADIUS = 1;

	public static final TicketController CONTROLLER = new TicketController(PostRider.asResource("rider"), (level, tickets) -> {
		PostOffice office = PostOffice.get(level.getServer());
		for (UUID owner : Set.copyOf(tickets.getEntityTickets().keySet()))
			if (!office.isRider(owner))
				tickets.removeAllTickets(owner);
	});

	private RiderChunks() {}

	/** The square of chunks centred on a chunk. */
	public static Set<ChunkPos> around(ChunkPos centre) {
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++)
			for (int dz = -RADIUS; dz <= RADIUS; dz++)
				chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
		return chunks;
	}

	/** Whether a chunk in the square round a centre is close enough to tick. */
	public static boolean ticks(ChunkPos centre, ChunkPos chunk) {
		return Math.abs(chunk.x - centre.x) <= TICKING_RADIUS && Math.abs(chunk.z - centre.z) <= TICKING_RADIUS;
	}

	/** Where each rider's square is centred, so a rider who vanishes can have his let go (see {@link #forget}). */
	private static final Map<UUID, ChunkPos> CENTRES = new HashMap<>();

	/** Moves a rider's loaded square from where it was (or nowhere) to around his chunk now. */
	public static void move(ServerLevel level, UUID rider, ChunkPos previous, ChunkPos now) {
		CENTRES.put(rider, now);
		Set<ChunkPos> keep = around(now);
		if (previous != null)
			for (ChunkPos chunk : around(previous)) {
				boolean ticked = ticks(previous, chunk);
				if (!keep.contains(chunk) || ticks(now, chunk) != ticked)
					CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, false, ticked);
			}
		for (ChunkPos chunk : keep)
			CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, true, ticks(now, chunk));
	}

	/** Lets go of a vanished rider's square, wherever it was last. */
	public static void forget(ServerLevel level, UUID rider) {
		ChunkPos centre = CENTRES.get(rider);
		if (centre != null)
			release(level, rider, centre);
	}

	public static void release(ServerLevel level, UUID rider, ChunkPos centre) {
		CENTRES.remove(rider);
		for (ChunkPos chunk : around(centre))
			CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, false, ticks(centre, chunk));
	}
}
