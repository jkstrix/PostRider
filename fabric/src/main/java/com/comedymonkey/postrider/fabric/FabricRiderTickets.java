package com.comedymonkey.postrider.fabric;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.comedymonkey.postrider.content.postman.RiderChunks;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The riders' chunk tickets on Fabric, doing what NeoForge 21.1's TicketController (postrider:rider) and its
 * ForcedChunkManager do for the mod:
 * <ul>
 * <li>tickets are held per rider (by UUID), in chunks held loaded or "ticking" (NeoForge's forceTicks: random ticks and
 * natural spawning with no player close enough, mixin.ServerChunkCacheMixin), one region ticket per rider and chunk;</li>
 * <li>the tickets are those NeoForge adds (its neoforge:entity and neoforge:entity_ticking types): region tickets at
 * distance 2, so ticket level 31 (entity ticking), as vanilla's forced chunks;</li>
 * <li>they're saved with each level (here as saved data, data/postrider_rider_tickets.dat in the level's folder; NeoForge
 * keeps them in the level's data/chunks.dat, with vanilla's forced chunks);</li>
 * <li>when the server loads its levels (MinecraftServer.prepareLevels, mixin.MinecraftServerMixin), right after vanilla
 * holds its own forced chunks again, the saved ones are handed to {@link RiderChunks#validate}, then the rest are held
 * again;</li>
 * <li>a level with any rider tickets in it keeps ticking its entities with no player in it, as NeoForge's patch to
 * ServerLevel.tick counts its tickets with vanilla's forced chunks (mixin.ServerLevelMixin).</li>
 * </ul>
 */
public final class FabricRiderTickets extends SavedData {
	/** The saved data's name: data/postrider_rider_tickets.dat in the level's folder. */
	static final String NAME = "postrider_rider_tickets";
	static final SavedData.Factory<FabricRiderTickets> FACTORY = new SavedData.Factory<>(FabricRiderTickets::new, FabricRiderTickets::load, null);

	/** NeoForge's neoforge:entity and neoforge:entity_ticking, the rider's UUID as the ticket's key. */
	private static final TicketType<UUID> RIDER = TicketType.create("postrider:rider", Comparator.<UUID>naturalOrder());
	private static final TicketType<UUID> RIDER_TICKING = TicketType.create("postrider:rider_ticking", Comparator.<UUID>naturalOrder());
	/** As NeoForge's (and vanilla's forced chunks): ticket level 33 - 2 = 31, the chunk's entities ticking. */
	private static final int DISTANCE = 2;

	/** Each rider's chunks, held loaded, and held ticking (NeoForge's TicketTracker). */
	private final Map<UUID, LongSet> chunks = new HashMap<>();
	private final Map<UUID, LongSet> tickingChunks = new HashMap<>();
	/** How many riders hold each chunk ticking (NeoForge's DistanceManager.forcedTickets). */
	private final Long2IntOpenHashMap tickingHolders = new Long2IntOpenHashMap();

	public FabricRiderTickets() {}

	/**
	 * Holds a chunk for a rider, or lets it go (Platform.forceChunk; NeoForge's TicketController.forceChunk): a newly held
	 * chunk is loaded at once, then the ticket added, as NeoForge does.
	 */
	static void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		FabricRiderTickets tickets = level.getDataStorage().computeIfAbsent(FACTORY, NAME);
		ChunkPos pos = new ChunkPos(chunkX, chunkZ);
		long chunk = pos.toLong();
		if (add) {
			if (tickets.add(owner, chunk, ticking)) {
				level.getChunk(chunkX, chunkZ);
				tickets.setDirty();
				level.getChunkSource().addRegionTicket(type(ticking), pos, DISTANCE, owner);
			}
		} else if (tickets.remove(owner, chunk, ticking)) {
			tickets.setDirty();
			level.getChunkSource().removeRegionTicket(type(ticking), pos, DISTANCE, owner);
		}
	}

	/** Whether some rider holds a chunk ticking: it gets random ticks and natural spawning with no player close enough. */
	public static boolean forcesTicks(ServerLevel level, ChunkPos chunk) {
		FabricRiderTickets tickets = level.getDataStorage().get(FACTORY, NAME);
		return tickets != null && tickets.tickingHolders.get(chunk.toLong()) > 0;
	}

	/** Whether any rider holds a chunk in the level (NeoForge's ForcedChunkManager.hasForcedChunks, for the riders). */
	public static boolean holdsAny(ServerLevel level) {
		FabricRiderTickets tickets = level.getDataStorage().get(FACTORY, NAME);
		return tickets != null && (!tickets.chunks.isEmpty() || !tickets.tickingChunks.isEmpty());
	}

	/**
	 * As the server loads its levels, after vanilla holds its own forced chunks again: hands the level's saved tickets to
	 * {@link RiderChunks#validate}, which drops some, and holds the rest again (NeoForge's
	 * ForcedChunkManager.reinstatePersistentChunks).
	 */
	public static void reinstate(ServerLevel level) {
		FabricRiderTickets tickets = level.getDataStorage().get(FACTORY, NAME);
		if (tickets == null)
			return;
		if (!tickets.chunks.isEmpty() || !tickets.tickingChunks.isEmpty())
			RiderChunks.validate(level, tickets.savedTickets());
		tickets.holdAll(level, false);
		tickets.holdAll(level, true);
	}

	private static TicketType<UUID> type(boolean ticking) {
		return ticking ? RIDER_TICKING : RIDER;
	}

	private Map<UUID, LongSet> tickets(boolean ticking) {
		return ticking ? tickingChunks : chunks;
	}

	/** True if the rider didn't hold the chunk that way already. */
	private boolean add(UUID owner, long chunk, boolean ticking) {
		if (!tickets(ticking).computeIfAbsent(owner, o -> new LongOpenHashSet()).add(chunk))
			return false;
		if (ticking)
			tickingHolders.addTo(chunk, 1);
		return true;
	}

	/** True if the rider held the chunk that way. */
	private boolean remove(UUID owner, long chunk, boolean ticking) {
		Map<UUID, LongSet> tickets = tickets(ticking);
		LongSet held = tickets.get(owner);
		if (held == null || !held.remove(chunk))
			return false;
		if (held.isEmpty())
			tickets.remove(owner);
		if (ticking && tickingHolders.addTo(chunk, -1) <= 1)
			tickingHolders.remove(chunk);
		return true;
	}

	private void holdAll(ServerLevel level, boolean ticking) {
		tickets(ticking).forEach((owner, held) -> {
			for (long chunk : held)
				level.getChunkSource().addRegionTicket(type(ticking), new ChunkPos(chunk), DISTANCE, owner);
		});
	}

	/** The saved tickets as RiderChunks.validate sees them (NeoForge's TicketHelper), gathered as validation starts. */
	private RiderChunks.SavedTickets savedTickets() {
		Map<UUID, LongSet> loaded = new HashMap<>();
		chunks.forEach((owner, held) -> loaded.put(owner, new LongOpenHashSet(held)));
		Set<UUID> owners = new HashSet<>(chunks.keySet());
		owners.addAll(tickingChunks.keySet());
		return new RiderChunks.SavedTickets() {
			@Override
			public Set<UUID> owners() {
				return owners;
			}

			@Override
			public LongCollection loadedChunks(UUID owner) {
				return loaded.getOrDefault(owner, new LongOpenHashSet());
			}

			@Override
			public void removeAll(UUID owner) {
				boolean removed = false;
				LongSet held = chunks.remove(owner);
				removed |= held != null;
				LongSet ticking = tickingChunks.remove(owner);
				if (ticking != null) {
					removed = true;
					for (long chunk : ticking)
						if (tickingHolders.addTo(chunk, -1) <= 1)
							tickingHolders.remove(chunk);
				}
				if (removed)
					setDirty();
			}

			@Override
			public void removeLoaded(UUID owner, long chunk) {
				if (remove(owner, chunk, false))
					setDirty();
			}
		};
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		Set<UUID> owners = new TreeSet<>(chunks.keySet());
		owners.addAll(tickingChunks.keySet());
		ListTag riders = new ListTag();
		for (UUID owner : owners) {
			CompoundTag rider = new CompoundTag();
			rider.putUUID("UUID", owner);
			LongSet held = chunks.get(owner);
			if (held != null)
				rider.put("Chunks", new LongArrayTag(held.toLongArray()));
			LongSet ticking = tickingChunks.get(owner);
			if (ticking != null)
				rider.put("TickingChunks", new LongArrayTag(ticking.toLongArray()));
			riders.add(rider);
		}
		tag.put("Riders", riders);
		return tag;
	}

	/** Read from the save: held again when the server loads its levels ({@link #reinstate}). */
	private static FabricRiderTickets load(CompoundTag tag, HolderLookup.Provider registries) {
		FabricRiderTickets tickets = new FabricRiderTickets();
		ListTag riders = tag.getList("Riders", Tag.TAG_COMPOUND);
		for (int i = 0; i < riders.size(); i++) {
			CompoundTag rider = riders.getCompound(i);
			if (!rider.hasUUID("UUID"))
				continue;
			UUID owner = rider.getUUID("UUID");
			for (long chunk : rider.getLongArray("Chunks"))
				tickets.add(owner, chunk, false);
			for (long chunk : rider.getLongArray("TickingChunks"))
				tickets.add(owner, chunk, true);
		}
		return tickets;
	}
}
