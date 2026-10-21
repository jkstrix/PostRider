package com.comedymonkey.ponyexpress.content.postman;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.post.PostOffice;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps the chunks around a working rider loaded, so he carries on riding when nobody is near him. The tickets are
 * saved with the world; when it loads, any left over from riders who are no longer working are dropped.
 */
public final class RiderChunks {
	/** Chunks loaded in each direction around him: 1 makes a 3x3 square. */
	public static final int RADIUS = 1;

	public static final TicketController CONTROLLER = new TicketController(PonyExpress.asResource("rider"), (level, tickets) -> {
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

	/** Moves a rider's loaded square from where it was (or nowhere) to around his chunk now. */
	public static void move(ServerLevel level, UUID rider, ChunkPos previous, ChunkPos now) {
		Set<ChunkPos> keep = around(now);
		if (previous != null)
			for (ChunkPos chunk : around(previous))
				if (!keep.contains(chunk))
					CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, false, true);
		for (ChunkPos chunk : keep)
			CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, true, true);
	}

	public static void release(ServerLevel level, UUID rider, ChunkPos centre) {
		for (ChunkPos chunk : around(centre))
			CONTROLLER.forceChunk(level, rider, chunk.x, chunk.z, false, true);
	}
}
