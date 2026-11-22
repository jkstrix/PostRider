package com.comedymonkey.postrider.content.postman;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.post.PostOffice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Keeps the chunks around a working rider loaded, so he carries on riding when nobody is near him: a square ticking
 * close round him, and a wider one loaded (but not ticking, so nothing grows or spawns there) for planning his route a
 * stretch ahead. The tickets are saved with the world; when it loads, any left over from riders who are no longer
 * working are dropped, and a working rider keeps only his ticking square: the rest (the wider square, and the chunks
 * held ahead along a plan that isn't saved) is held again once he's going, and would otherwise be held for ever.
 */
public final class RiderChunks {
	/** Chunks loaded in each direction around him by default: 3 makes a 7x7 square, reaching past a 48-block stretch. */
	public static final int RADIUS = 3;

	/** Chunks loaded in each direction for the stretch in use: enough to plan a whole stretch ahead from anywhere in his chunk. */
	public static int radius() {
		return Math.max(TICKING_RADIUS, (int) Math.ceil(Journey.stretch() / 16));
	}
	/** Of those, the ones that tick (mobs, crops, his own goings-on): 1 makes the 3x3 square he's in the middle of. */
	public static final int TICKING_RADIUS = 1;

	public static final TicketController CONTROLLER = new TicketController(PostRider.asResource("rider"), (level, tickets) -> {
		PostOffice office = PostOffice.get(level.getServer());
		for (UUID owner : Set.copyOf(tickets.getEntityTickets().keySet())) {
			if (!office.isRider(owner)) {
				tickets.removeAllTickets(owner);
				continue;
			}
			for (long chunk : new LongArrayList(tickets.getEntityTickets().get(owner).normal()))
				tickets.removeTicket(owner, chunk, false);
		}
	});

	private RiderChunks() {}

	/** The square of chunks centred on a chunk, for the stretch in use. */
	public static Set<ChunkPos> around(ChunkPos centre) {
		return around(centre, radius());
	}

	public static Set<ChunkPos> around(ChunkPos centre, int radius) {
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (int dx = -radius; dx <= radius; dx++)
			for (int dz = -radius; dz <= radius; dz++)
				chunks.add(new ChunkPos(centre.x() + dx, centre.z() + dz));
		return chunks;
	}

	/** Whether a chunk in the square round a centre is close enough to tick. */
	public static boolean ticks(ChunkPos centre, ChunkPos chunk) {
		return Math.abs(chunk.x() - centre.x()) <= TICKING_RADIUS && Math.abs(chunk.z() - centre.z()) <= TICKING_RADIUS;
	}

	/** A rider's loaded square, as it was loaded, so it's let go the same size even if the setting has changed since. */
	private record Square(ChunkPos centre, int radius) {}

	private static final Map<UUID, Square> SQUARES = new HashMap<>();

	/** The size of a rider's square round a centre: as loaded if we know, otherwise as the setting is now. */
	private static int radiusOf(UUID rider, ChunkPos centre) {
		Square square = SQUARES.get(rider);
		return square != null && square.centre().equals(centre) ? square.radius() : radius();
	}

	/** Moves a rider's loaded square from where it was (or nowhere) to around his chunk now. */
	public static void move(ServerLevel level, UUID rider, ChunkPos previous, ChunkPos now) {
		int radius = radius();
		Set<ChunkPos> keep = around(now, radius);
		if (previous != null)
			for (ChunkPos chunk : around(previous, radiusOf(rider, previous))) {
				boolean ticked = ticks(previous, chunk);
				if (!keep.contains(chunk) || ticks(now, chunk) != ticked)
					CONTROLLER.forceChunk(level, rider, chunk.x(), chunk.z(), false, ticked);
			}
		for (ChunkPos chunk : keep)
			CONTROLLER.forceChunk(level, rider, chunk.x(), chunk.z(), true, ticks(now, chunk));
		SQUARES.put(rider, new Square(now, radius));
	}

	/**
	 * Lets go of a vanished rider's square. If it hasn't moved since the server started there's no record of it, so
	 * a square a little larger than his round where he was last seen.
	 */
	public static void forget(ServerLevel level, UUID rider, @Nullable BlockPos lastSeen) {
		Square square = SQUARES.get(rider);
		if (square != null)
			release(level, rider, square.centre());
		else if (lastSeen != null)
			releaseAround(level, rider, ChunkPos.containing(lastSeen), radius() + 1);
	}

	/** Forgets every square (the server is stopping: the tickets are saved with the world, and sorted out when it loads). */
	public static void clear() {
		SQUARES.clear();
	}

	public static void release(ServerLevel level, UUID rider, ChunkPos centre) {
		int radius = radiusOf(rider, centre);
		SQUARES.remove(rider);
		releaseAround(level, rider, centre, radius);
	}

	private static void releaseAround(ServerLevel level, UUID rider, ChunkPos centre, int radius) {
		for (ChunkPos chunk : around(centre, radius))
			CONTROLLER.forceChunk(level, rider, chunk.x(), chunk.z(), false, ticks(centre, chunk));
	}
}
